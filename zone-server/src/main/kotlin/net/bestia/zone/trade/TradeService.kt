package net.bestia.zone.trade

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.AccountDisconnectedEvent
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.account.master.skill.BasicSkillGate
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.trade.Trading
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Face-to-face trading between two players.
 *
 * ### The shape of it
 *
 * Sessions live in memory here, the way party invitations do. What is durable is narrower, and deliberately
 * so: the moment an item is offered it is *reserved in place* on its owner's container
 * ([InventoryService.reserveForTrade]) and taken out of the live ECS inventory, so it can no longer be
 * dropped, eaten or spent on a craft while it sits in the window. The row never leaves its owner, which is
 * what makes crash recovery a single column update rather than a reconciliation.
 *
 * The exchange itself is one transaction ([InventoryService.settleTrade]) and happens once, at the end. Up to
 * that point nothing has changed hands, so every way a trade can end without one - declined, cancelled,
 * walked apart, disconnected, failed to commit - is the same operation: give the reservations back.
 *
 * ### Ordering
 *
 * Every transition runs inside `synchronized(session)`, because messages arrive on socket threads while the
 * range sweep runs on the tick thread. Work that touches the database or the world lock stays outside that
 * block where it can, and is handed to [AsyncJobExecutor] when the caller is the tick thread.
 *
 * **Nothing here ever holds a session monitor while waiting for the world lock.** That is what makes it safe
 * for the tick thread - which holds the world lock for the whole tick - to take a session monitor on its way
 * through [handleTradeInterrupted]. Every method below alternates the two rather than nesting them; nesting a
 * `world.modify` inside a `synchronized(session)` would close the cycle and hang the server.
 */
@Service
class TradeService(
  private val world: WorldView,
  private val connectionInfoService: ConnectionInfoService,
  private val masterRepository: MasterRepository,
  private val inventoryService: InventoryService,
  private val outMessageProcessor: OutMessageProcessor,
  private val basicSkillGate: BasicSkillGate,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  private val sessions = ConcurrentHashMap<Long, TradeSession>()

  /**
   * Which trade an account is tied up in, pending or open. Claimed with `putIfAbsent`, which is what makes
   * two people asking the same player at the same moment resolve to one trade rather than two.
   */
  private val tradeByAccount = ConcurrentHashMap<AccountId, Long>()

  private val scheduler: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "trade-expiry") }

  private val nextTradeId = AtomicLong(1L)

  // ------------------------------------------------------------------ opening

  /**
   * Asks the player behind [targetEntityId] to trade.
   *
   * [targetEntityId] is the one client-supplied entity id this feature accepts, and it is checked rather than
   * trusted: it has to name a live entity some account is currently driving, that account has to be somebody
   * else, and the two have to be within reach.
   */
  fun requestTrade(accountId: AccountId, targetEntityId: EntityId) {
    if (!basicSkillGate.mayTrade(accountId)) {
      deny(accountId, OpError.BASIC_SKILL_TRADE_LOCKED)
      return
    }

    val requesterEntityId = try {
      connectionInfoService.getActiveEntityId(accountId)
    } catch (e: Exception) {
      LOG.warn(e) { "Account $accountId asked to trade without an active entity" }
      return
    }

    if (requesterEntityId == targetEntityId) {
      LOG.warn { "Account $accountId asked to trade with itself" }
      return
    }

    val targetAccountId = world.read { get(targetEntityId, Account::class)?.accountId }
    if (targetAccountId == null || targetAccountId == accountId) {
      deny(accountId, OpError.TRADE_TARGET_UNAVAILABLE)
      return
    }

    // The clicked entity has to be the one they are actually driving; anything else is a stale id from a
    // client that has not caught up with them switching bestia.
    val targetActive = try {
      connectionInfoService.getActiveEntityId(targetAccountId)
    } catch (_: Exception) {
      null
    }

    if (targetActive != targetEntityId || !basicSkillGate.mayTrade(targetAccountId)) {
      deny(accountId, OpError.TRADE_TARGET_UNAVAILABLE)
      return
    }

    if (!withinReach(requesterEntityId, targetEntityId)) {
      deny(accountId, OpError.TRADE_OUT_OF_RANGE)
      return
    }

    val tradeId = nextTradeId.getAndIncrement()

    if (!claim(accountId, targetAccountId, tradeId)) {
      deny(accountId, OpError.TRADE_TARGET_UNAVAILABLE)
      return
    }

    val session = try {
      TradeSession(
        tradeId = tradeId,
        requester = sideFor(accountId, requesterEntityId),
        target = sideFor(targetAccountId, targetEntityId),
      )
    } catch (e: Exception) {
      LOG.warn(e) { "Could not open trade $tradeId between $accountId and $targetAccountId" }
      release(accountId, targetAccountId, tradeId)
      deny(accountId, OpError.TRADE_TARGET_UNAVAILABLE)
      return
    }

    sessions[tradeId] = session

    scheduler.schedule({ expire(tradeId) }, REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    outMessageProcessor.sendToPlayer(
      targetAccountId,
      TradeRequestSMSG(tradeId, requesterEntityId, session.requester.masterName)
    )

    LOG.debug { "Trade $tradeId requested by $accountId of $targetAccountId" }
  }

  /** Answers the prompt. Only the asked player may answer it, and only once. */
  fun answerRequest(accountId: AccountId, tradeId: Long, accept: Boolean) {
    val session = sessions[tradeId] ?: return

    val opened = synchronized(session) {
      if (session.status != TradeStatus.PENDING || accountId != session.target.accountId) {
        return
      }

      session.status = if (accept) TradeStatus.OPEN else TradeStatus.CLOSED
      accept
    }

    if (!opened) {
      finish(session, TradeEndReason.DECLINED, byAccountId = accountId)
      return
    }

    session.sides().forEach { side ->
      val partner = session.partnerOf(side)
      world.modify(side.entityId) { id -> add(id, Trading(tradeId, partner.entityId)) }
    }

    pushState(session)

    LOG.debug { "Trade $tradeId accepted by $accountId" }
  }

  // ------------------------------------------------------------------- offers

  /**
   * Puts an item into the caller's side of the window.
   *
   * The durable commit comes first and decides which physical item leaves, then the live inventory is brought
   * into line - the ordering `DropItemHandler` established, and the one that does not duplicate the item if
   * the process dies in between. Both happen inside one world-lock scope, so a concurrent drop of the same
   * item lands strictly before or after and never in the middle.
   */
  fun offerItem(accountId: AccountId, tradeId: Long, itemId: Long, uniqueId: Long, amount: Int) {
    val session = sessions[tradeId] ?: return

    val side = synchronized(session) {
      if (session.status != TradeStatus.OPEN) {
        null
      } else {
        session.sideOf(accountId)?.takeUnless { it.locked }
      }
    }

    // An honest client offers nothing while locked and nothing it does not hold, so re-sending the truth is
    // both the correction and the whole answer - no code of its own for a state only a broken client reaches.
    if (side == null || amount <= 0) {
      resendTo(session, accountId)
      return
    }

    val reserved = world.modify(side.entityId) { id ->
      val inventory = get(id, Inventory::class) ?: return@modify null

      val reserved = inventoryService.reserveForTrade(side.masterId, tradeId, itemId, uniqueId, amount)
        ?: return@modify null

      if (!removeFromLive(inventory, reserved)) {
        // The durable side gave the item up but the live mirror disagrees about holding it. Put it back rather
        // than let the two drift; the player sees the offer simply not appear.
        inventoryService.releaseTradeReservation(side.masterId, tradeId, reserved.offerSlotId)
        LOG.warn {
          "Trade $tradeId: master ${side.masterId} reserved item ${reserved.itemId} " +
                  "that its live inventory does not hold"
        }

        return@modify null
      }

      reserved
    }

    if (reserved == null) {
      resendTo(session, accountId)
      return
    }

    synchronized(session) {
      side.offer += reserved
      session.clearReadiness()
    }

    pushState(session)
  }

  /** Takes one line back out of the caller's own offer. */
  fun retractItem(accountId: AccountId, tradeId: Long, offerSlotId: Long) {
    val session = sessions[tradeId] ?: return

    val side = synchronized(session) {
      if (session.status != TradeStatus.OPEN) {
        null
      } else {
        session.sideOf(accountId)
          ?.takeUnless { it.locked }
          ?.takeIf { held -> held.offer.any { it.offerSlotId == offerSlotId } }
      }
    }

    if (side == null) {
      resendTo(session, accountId)
      return
    }

    val released = world.modify(side.entityId) { id ->
      val inventory = get(id, Inventory::class) ?: return@modify null
      val released = inventoryService.releaseTradeReservation(side.masterId, tradeId, offerSlotId)
        ?: return@modify null

      inventory.addItem(released.toLiveItem())

      released
    }

    if (released == null) {
      resendTo(session, accountId)
      return
    }

    synchronized(session) {
      side.offer.removeIf { it.offerSlotId == offerSlotId }
      session.clearReadiness()
    }

    pushState(session)
  }

  // -------------------------------------------------------------- finalisation

  /**
   * Locks or unlocks the caller's side.
   *
   * Unlocking stops working once both sides are locked: from there the contents are frozen and the only moves
   * left are confirming and cancelling, which is what keeps a confirmation from racing an unlock.
   */
  fun setLock(accountId: AccountId, tradeId: Long, locked: Boolean) {
    val session = sessions[tradeId] ?: return

    val changed = synchronized(session) {
      val side = session.sideOf(accountId) ?: return
      if (session.status != TradeStatus.OPEN && session.status != TradeStatus.LOCKED) return
      if (session.status == TradeStatus.LOCKED && !locked) {
        false
      } else {
        side.locked = locked
        session.status = if (session.bothLocked) TradeStatus.LOCKED else TradeStatus.OPEN
        true
      }
    }

    if (!changed) {
      resendTo(session, accountId)
      return
    }

    pushState(session)
  }

  /** The final commitment. Runs the exchange once both sides have given it. */
  fun confirm(accountId: AccountId, tradeId: Long) {
    val session = sessions[tradeId] ?: return

    val readyToSettle = synchronized(session) {
      val side = session.sideOf(accountId) ?: return
      if (session.status != TradeStatus.LOCKED) {
        false
      } else {
        side.confirmed = true

        if (session.bothConfirmed) {
          session.status = TradeStatus.SETTLING
          true
        } else {
          false
        }
      }
    }

    if (readyToSettle) {
      settle(session)
    } else {
      pushState(session)
    }
  }

  /** Called off by one of the two players. */
  fun cancel(accountId: AccountId, tradeId: Long) {
    val session = sessions[tradeId] ?: return

    val wasPending = synchronized(session) {
      if (session.sideOf(accountId) == null) return
      if (session.status == TradeStatus.SETTLING || session.status == TradeStatus.CLOSED) return

      val pending = session.status == TradeStatus.PENDING
      session.status = TradeStatus.CLOSED
      pending
    }

    // Backing out of a prompt is a decline, not a cancellation - the requester should hear the right thing.
    val reason = if (wasPending && accountId == session.target.accountId) {
      TradeEndReason.DECLINED
    } else {
      TradeEndReason.CANCELLED
    }

    finish(session, reason, byAccountId = accountId)
  }

  /**
   * Ends a trade from somewhere that must not block: the range sweep runs on the tick thread, and giving the
   * reservations back means a transaction plus two world-lock scopes.
   */
  fun cancelAsync(tradeId: Long, reason: TradeEndReason) {
    val session = sessions[tradeId] ?: return

    synchronized(session) {
      if (session.status == TradeStatus.SETTLING || session.status == TradeStatus.CLOSED) return
      session.status = TradeStatus.CLOSED
    }

    asyncJobExecutor.submit(tradeId) { finish(session, reason, byAccountId = null) }
  }

  /**
   * The world ended it - they walked apart, or an entity went away.
   *
   * Reached by event rather than by [TradeRangeSystem] calling in, because a system holding this service
   * would close a construction cycle back through the ECS world. Returns at once: the work is a worker's.
   */
  @EventListener
  fun handleTradeInterrupted(event: TradeInterruptedEvent) {
    cancelAsync(event.tradeId, event.reason)
  }

  @EventListener
  fun handleAccountDisconnected(event: AccountDisconnectedEvent) {
    val tradeId = tradeByAccount[event.accountId] ?: return
    val session = sessions[tradeId] ?: return

    synchronized(session) {
      if (session.status == TradeStatus.SETTLING || session.status == TradeStatus.CLOSED) return
      session.status = TradeStatus.CLOSED
    }

    finish(session, TradeEndReason.CANCELLED, byAccountId = event.accountId)
  }

  // ----------------------------------------------------------------- internals

  private fun settle(session: TradeSession) {
    val requester = session.requester
    val target = session.target

    val settlement = try {
      inventoryService.settleTrade(
        tradeId = session.tradeId,
        masterAId = requester.masterId,
        masterBId = target.masterId,
        expectedA = requester.offer.map { it.offerSlotId }.toSet(),
        expectedB = target.offer.map { it.offerSlotId }.toSet(),
      )
    } catch (e: Exception) {
      LOG.error(e) { "Trade ${session.tradeId} could not be settled; nothing changed hands" }
      finish(session, TradeEndReason.FAILED, byAccountId = null)
      return
    }

    // Past the commit. Both offers left their live inventories when they were offered, so each side only has
    // to gain what the other gave. Somebody who went offline in between gets theirs from the database on their
    // next login - it is already durable.
    grantToLive(requester.entityId, settlement.toMasterA)
    grantToLive(target.entityId, settlement.toMasterB)

    synchronized(session) { session.status = TradeStatus.CLOSED }

    if (sessions.remove(session.tradeId) != null) {
      releaseHolds(session)
      pushFinalState(session, TradeStateSMSG.Status.COMPLETED)
    }

    LOG.debug { "Trade ${session.tradeId} settled between ${requester.masterId} and ${target.masterId}" }
  }

  /**
   * The one way a trade ends without an exchange: give every reservation back, take the markers off both
   * entities, tell whoever needs telling.
   *
   * The `sessions.remove` guard makes this idempotent, which it has to be - an expiry timer firing on an
   * already-answered request, and a disconnect racing a cancel, both arrive here.
   */
  private fun finish(session: TradeSession, reason: TradeEndReason, byAccountId: AccountId?) {
    if (sessions.remove(session.tradeId) == null) {
      return
    }

    session.sides().forEach { side ->
      val returned = inventoryService.releaseAllTradeReservations(side.masterId, session.tradeId)
      if (returned.isNotEmpty()) {
        grantToLive(side.entityId, returned)
      }
    }

    releaseHolds(session)
    pushFinalState(session, TradeStateSMSG.Status.CANCELLED)
    report(session, reason, byAccountId)

    LOG.debug { "Trade ${session.tradeId} ended: $reason" }
  }

  private fun releaseHolds(session: TradeSession) {
    session.sides().forEach { side ->
      tradeByAccount.remove(side.accountId, session.tradeId)
      world.modify(side.entityId) { id -> remove(id, Trading::class) }
    }
  }

  /** Which of the `TRADE_*` codes each side hears, and whether they hear anything at all. */
  private fun report(session: TradeSession, reason: TradeEndReason, byAccountId: AccountId?) {
    when (reason) {
      TradeEndReason.EXPIRED ->
        deny(session.requester.accountId, OpError.TRADE_TARGET_UNAVAILABLE)

      TradeEndReason.DECLINED ->
        deny(session.requester.accountId, OpError.TRADE_DECLINED, session.target.masterName)

      // Whoever pressed Cancel does not need telling that they pressed Cancel.
      TradeEndReason.CANCELLED -> {
        val actor = byAccountId?.let { session.sideOf(it) }
        session.sides()
          .filter { it !== actor }
          .forEach { side -> deny(side.accountId, OpError.TRADE_CANCELLED, session.partnerOf(side).masterName) }
      }

      // Neither of them chose it, so both are told, and each is told about the other.
      TradeEndReason.WALKED_AWAY -> session.sides()
        .forEach { side -> deny(side.accountId, OpError.TRADE_WALKED_AWAY, session.partnerOf(side).masterName) }

      TradeEndReason.PARTNER_GONE -> session.sides()
        .forEach { side -> deny(side.accountId, OpError.TRADE_CANCELLED, session.partnerOf(side).masterName) }

      TradeEndReason.FAILED ->
        session.sides().forEach { deny(it.accountId, OpError.TRADE_FAILED) }
    }
  }

  private fun expire(tradeId: Long) {
    val session = sessions[tradeId] ?: return

    synchronized(session) {
      if (session.status != TradeStatus.PENDING) return
      session.status = TradeStatus.CLOSED
    }

    finish(session, TradeEndReason.EXPIRED, byAccountId = null)
  }

  private fun sideFor(accountId: AccountId, entityId: EntityId): TradeSession.Side {
    val masterId = connectionInfoService.getMasterId(accountId)
    val master = masterRepository.findByIdOrThrow(masterId)

    return TradeSession.Side(
      accountId = accountId,
      masterId = masterId,
      entityId = entityId,
      masterName = master.name,
    )
  }

  /** Claims both accounts for [tradeId], or neither. */
  private fun claim(one: AccountId, other: AccountId, tradeId: Long): Boolean {
    if (tradeByAccount.putIfAbsent(one, tradeId) != null) {
      return false
    }

    if (tradeByAccount.putIfAbsent(other, tradeId) != null) {
      tradeByAccount.remove(one, tradeId)
      return false
    }

    return true
  }

  private fun release(one: AccountId, other: AccountId, tradeId: Long) {
    tradeByAccount.remove(one, tradeId)
    tradeByAccount.remove(other, tradeId)
  }

  private fun withinReach(one: EntityId, other: EntityId): Boolean = world.read {
    val onePos = get(one, Position::class)?.toVec3L() ?: return@read false
    val otherPos = get(other, Position::class)?.toVec3L() ?: return@read false

    onePos.distance(otherPos) <= MAX_TRADE_RANGE
  }

  /** Mirrors a durable reservation into the live inventory by taking the same physical item out of it. */
  private fun removeFromLive(inventory: Inventory, reserved: ReservedItem): Boolean = when {
    reserved.uniqueId != 0L -> inventory.removeByUniqueId(reserved.uniqueId)
    !reserved.stackable -> inventory.removeInstanceOf(reserved.itemId)
    else -> inventory.removeFromStack(reserved.itemId, reserved.amount)
  }

  private fun grantToLive(entityId: EntityId, items: List<ReservedItem>) {
    world.modify(entityId) { id ->
      val inventory = get(id, Inventory::class)

      if (inventory == null) {
        // Offline, or mid-despawn. The database already holds the items; they turn up on the next login.
        LOG.debug { "Entity $entityId has no live inventory to receive ${items.size} traded item(s)" }
        return@modify
      }

      items.forEach { inventory.addItem(it.toLiveItem()) }
    }
  }

  private fun pushState(session: TradeSession) {
    session.sides().forEach { side -> outMessageProcessor.sendToPlayer(side.accountId, stateFor(session, side)) }
  }

  /**
   * Re-sends the truth to one side, which is how every refusal is answered.
   *
   * Silent unless a window is actually open: a message naming a trade that has not been accepted yet can only
   * come from a client that made it up, and answering it with a state would pop a trade window on somebody
   * who is still looking at the prompt.
   */
  private fun resendTo(session: TradeSession, accountId: AccountId) {
    val side = session.sideOf(accountId) ?: return

    val open = synchronized(session) {
      session.status == TradeStatus.OPEN || session.status == TradeStatus.LOCKED
    }
    if (!open) {
      return
    }

    outMessageProcessor.sendToPlayer(accountId, stateFor(session, side))
  }

  private fun pushFinalState(session: TradeSession, status: TradeStateSMSG.Status) {
    session.sides().forEach { side ->
      outMessageProcessor.sendToPlayer(side.accountId, stateFor(session, side, status))
    }
  }

  private fun stateFor(
    session: TradeSession,
    side: TradeSession.Side,
    forcedStatus: TradeStateSMSG.Status? = null,
  ): TradeStateSMSG = synchronized(session) {
    val partner = session.partnerOf(side)

    TradeStateSMSG(
      tradeId = session.tradeId,
      status = forcedStatus ?: if (session.status == TradeStatus.LOCKED) {
        TradeStateSMSG.Status.LOCKED
      } else {
        TradeStateSMSG.Status.OPEN
      },
      partnerMasterName = partner.masterName,
      partnerEntityId = partner.entityId,
      ownOffer = side.offer.toList(),
      partnerOffer = partner.offer.toList(),
      ownLocked = side.locked,
      partnerLocked = partner.locked,
      ownConfirmed = side.confirmed,
      partnerConfirmed = partner.confirmed,
    )
  }

  private fun deny(accountId: AccountId, code: OpError, vararg args: String) {
    outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(code, args.toList()))
  }

  private fun ReservedItem.toLiveItem() = Inventory.Item(
    itemId = itemId,
    amount = amount,
    weight = weight,
    uniqueId = uniqueId,
    stackable = stackable,
    durability = durability,
    maxDurability = maxDurability,
    slots = slots,
    upgradeLevel = upgradeLevel,
  )

  companion object {
    /**
     * How long an unanswered request stands. Shorter than a party invitation's minute because the two are
     * standing in front of each other: if it has not been answered in half a minute, it is not going to be.
     */
    const val REQUEST_TIMEOUT_SECONDS = 30L

    /**
     * How close the two have to be, in tiles - to start a trade and to keep one, which is why
     * [net.bestia.zone.ecs.trade.TradeRangeSystem] reads this one rather than declaring its own. Ten is far
     * enough to survive both parties shuffling about and close enough that a trade stays a face-to-face
     * gesture rather than a way to hand things across a town.
     *
     * `Vec3L.distance` is horizontal only and truncates, so this errs lenient and ignores height -
     * consistent with every other range check in the codebase.
     */
    const val MAX_TRADE_RANGE = 10L

    private val LOG = KotlinLogging.logger { }
  }
}
