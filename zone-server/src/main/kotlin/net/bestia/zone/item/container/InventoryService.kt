package net.bestia.zone.item.container

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.bestia.PlayerBestiaRepository
import net.bestia.zone.bestia.findByIdOrThrow
import net.bestia.zone.item.Item
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.findByIdentifierOrThrow
import net.bestia.zone.item.instance.ItemInstance
import net.bestia.zone.item.instance.ItemInstanceRepository
import net.bestia.zone.item.instance.findByIdOrThrow
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The single durable-inventory entry point. Adds/removes items on an owner's [ItemContainer] and
 * owns the rule for when a plain stackable pile vs. a unique [ItemInstance] is created - callers
 * (message handlers, ECS systems, seeding) never touch [ContainerSlot]/[ItemInstance] directly.
 *
 * Deliberately has no dependency on the ECS world: callers that also need the live ECS `Inventory`
 * component updated go through [net.bestia.zone.ecs.item.ObtainItemIntent] instead, which mutates
 * the ECS side on the tick thread and hands the DB write here off to
 * [net.bestia.zone.ecs.core.AsyncJobExecutor]. This class does not check carry weight or item
 * count limits - the caller/service is responsible for that.
 */
@Service
class InventoryService(
  private val masterRepository: MasterRepository,
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val itemRepository: ItemRepository,
  private val itemInstanceRepository: ItemInstanceRepository,
) {

  /**
   * Adds an item directly to a managed master's container (e.g. during DB seeding). Does not touch
   * any ECS inventory component.
   *
   * Always re-fetches [master] fresh by id rather than trusting the passed-in instance: callers
   * (e.g. `DevDataBootstrapRunner`) may reuse the same `Master` reference across several of these
   * calls, each its own transaction. Since `save()` on an already-persisted entity goes through
   * `merge()` - which returns a new managed copy rather than updating the argument in place - a
   * reused, once-already-saved instance would still show its previously-added slots with `id == 0`
   * from this method's point of view, and cascading persist would re-insert them as new rows on
   * every subsequent call.
   */
  @Transactional
  fun addItem(master: Master, itemIdentifier: String, amount: Int) {
    val freshMaster = masterRepository.findByIdOrThrow(master.id)
    val item = itemRepository.findByIdentifierOrThrow(itemIdentifier)
    grant(freshMaster.container, item, amount, uniqueId = 0L)
    masterRepository.save(freshMaster)
  }

  /**
   * Grants items to a master's own container and saves immediately. Used for critical item
   * transactions (e.g. looting) where the corresponding ECS/ground entity has already been removed
   * first to avoid duplication. Pass [uniqueId] != 0 to re-attach an existing instance that was
   * lying on the ground; pass 0 for a fresh grant, and whether it becomes a stack or a new instance
   * is decided from the item template.
   */
  @Transactional
  fun grantToMaster(masterId: Long, item: Item, amount: Int, uniqueId: Long = 0L) {
    val master = masterRepository.findByIdOrThrow(masterId)
    grant(master.container, item, amount, uniqueId)
    masterRepository.save(master)
  }

  /**
   * Mints a fresh instance of [item] into a master's container and answers it.
   *
   * [grantToMaster] cannot serve here because it returns nothing, and the caller that needs this needs the
   * instance itself: a chart is a row that *references* its item instance, so the id has to come back out of
   * the grant rather than be looked up afterwards - a lookup would have to guess which of several identical
   * instances was the new one.
   *
   * Always an instance, never a stack, whatever the template says about stacking: an item something else hangs
   * per-instance state off has to have an instance to hang it off.
   */
  @Transactional
  fun mintInstanceForMaster(masterId: Long, item: Item): ItemInstance {
    val master = masterRepository.findByIdOrThrow(masterId)
    val instance = itemInstanceRepository.save(ItemInstance(item = item))

    master.container.addInstance(instance)
    masterRepository.save(master)

    return instance
  }

  /**
   * Removes one item (identified by its template id) from a master's container, preferring a unique
   * instance so its identity is preserved for whatever happens next (e.g. dropping it to the
   * ground). The removal must be durable before the caller mutates the in-memory/ECS state, to
   * avoid item duplication on a crash. The returned instance - if any - is intentionally kept alive
   * in the DB, just detached from the container. Returns null if the item was not present.
   *
   * [uniqueId] pins the choice to one copy when the caller knows which one it means; see
   * [ItemContainer.removeOne].
   */
  @Transactional
  fun removeOneFromMaster(
    masterId: Long,
    itemId: Long,
    amount: Int,
    uniqueId: Long = 0L
  ): ItemContainer.RemovedItem? {
    val master = masterRepository.findByIdOrThrow(masterId)
    val removed = master.container.removeOne(itemId, amount, uniqueId) ?: return null
    masterRepository.save(master)
    return removed
  }

  /**
   * Durably marks an item as worn in [slot] by the given owner - a master when
   * [playerBestiaId] is null, otherwise that player bestia. The ECS
   * [net.bestia.zone.ecs.item.Equipment] component has already been mutated by the caller on the
   * tick thread; this is the write-behind half, so a mismatch here (item gone, slot taken) only
   * means the change does not survive a restart, it never corrupts live state.
   */
  @Transactional
  fun equip(
    masterId: Long,
    playerBestiaId: PlayerBestiaId?,
    itemId: Long,
    uniqueId: Long,
    slot: EquipmentSlot
  ): Boolean {
    return withOwnerContainer(masterId, playerBestiaId) { it.equip(itemId, uniqueId, slot) }
  }

  @Transactional
  fun unequip(masterId: Long, playerBestiaId: PlayerBestiaId?, slot: EquipmentSlot): Boolean {
    return withOwnerContainer(masterId, playerBestiaId) { it.unequip(slot) != null }
  }

  /**
   * Takes every stack in [inputs] off a master's container in one transaction, or nothing at all.
   *
   * All-or-nothing is the point: a craft that consumed two of its three materials and then found the
   * third missing would have eaten them for no result and no message. The check and the removals share a
   * transaction, so a concurrent drop can only land before or after, never between them.
   *
   * @return false, having changed nothing, when any input is not held in full
   */
  @Transactional
  fun consumeAll(masterId: Long, inputs: List<Pair<Long, Int>>): Boolean {
    if (inputs.isEmpty()) return true

    val master = masterRepository.findByIdOrThrow(masterId)

    // Summed per item first, so a recipe naming the same material twice is checked against the total
    // rather than against whichever line happened to be looked at last.
    val required = inputs.groupBy({ it.first }, { it.second }).mapValues { (_, amounts) -> amounts.sum() }

    if (required.any { (itemId, amount) -> !master.container.hasItem(itemId, amount) }) {
      return false
    }

    required.forEach { (itemId, amount) ->
      if (!master.container.removeStackable(itemId, amount)) {
        // Reached only if an input is held solely as instance slots, which `hasItem` counts and
        // `removeStackable` will not touch. Rolling back is the honest answer: the alternative is
        // consuming part of the recipe.
        throw IllegalStateException(
          "Master $masterId holds item $itemId only as unique instances; recipe inputs must be stackable"
        )
      }
    }

    masterRepository.save(master)

    return true
  }

  /**
   * Reads the [ItemInstance] a master is holding under [uniqueId], or null when they are not holding it -
   * which includes holding it *worn* or promised to a trade, because every craft that changes an item
   * refuses both.
   *
   * Refusing worn gear is the same rule the container applies to dropping and consuming: you cannot alter
   * what you are wearing without taking it off. It also keeps a resolving craft from having to reach into
   * the `Equipment` component, whose entries are immutable snapshots.
   */
  @Transactional(readOnly = true)
  fun heldInstance(masterId: Long, uniqueId: Long): ItemInstance? {
    if (uniqueId == 0L) return null

    val master = masterRepository.findByIdOrThrow(masterId)

    return master.container.slots
      .firstOrNull { it.uniqueId == uniqueId && it.isFree }
      ?.itemInstance
  }

  /**
   * Applies [change] to a held, not-worn instance and saves it.
   *
   * The instance is re-read inside the transaction rather than taking one handed in by the caller: a
   * craft resolves a tick or more after it was validated, and the row may have moved on.
   *
   * @return the mutated instance, or null when it is no longer held
   */
  @Transactional
  fun updateInstance(masterId: Long, uniqueId: Long, change: (ItemInstance) -> Unit): ItemInstance? {
    val instance = heldInstance(masterId, uniqueId) ?: return null

    change(instance)

    return itemInstanceRepository.save(instance)
  }

  /**
   * Destroys a held, not-worn instance outright - what a failed rune-slot cut does to the item.
   *
   * Deletes the row rather than only detaching it, which is the opposite of every other removal path here
   * (see [removeOneFromMaster], whose whole point is keeping the instance alive in transit). Nothing is
   * in transit: the item is gone.
   *
   * @return false when the instance was not held
   */
  @Transactional
  fun destroyInstance(masterId: Long, uniqueId: Long): Boolean {
    val master = masterRepository.findByIdOrThrow(masterId)
    val taken = master.container.takeInstance(uniqueId) ?: return false

    masterRepository.save(master)
    itemInstanceRepository.delete(taken)

    return true
  }

  /**
   * Promises an item to an open trade: it stays on the owner's container but stops being reachable by every
   * other path, so it can no longer be dropped, eaten or spent on a craft while it sits in the trade window.
   *
   * Must be durable before the caller mirrors the removal into the live ECS inventory, for the reason
   * [removeOneFromMaster] gives: the database decides which physical item left, and doing it the other way
   * round duplicates the item on a crash.
   *
   * [uniqueId] names the exact instance. It may legitimately be 0 for an instance item obtained this session
   * whose row was minted after the live inventory copy was made, in which case any free copy of the template
   * is taken instead - the same fallback [ItemContainer.equip] makes.
   *
   * Flushes, because the caller needs the id of the slot to name this offer line on the wire, and a deferred
   * insert would hand back 0.
   *
   * The id is read back off what `save` returned rather than off the instance that was mutated: `save` goes
   * through `merge()`, which may answer with a managed copy, and it is that copy the generated id lands on -
   * the same trap [addItem] documents. The new line is the one reserved for this trade that was not reserved
   * for it a moment ago, which identifies it whether a slot was split off or an existing one merely marked.
   *
   * @return what was promised, or null when it is not freely held in that quantity
   */
  @Transactional
  fun reserveForTrade(masterId: Long, tradeId: Long, itemId: Long, uniqueId: Long, amount: Int): ReservedItem? {
    require(amount > 0) { "amount > 0 required, was $amount" }

    val master = masterRepository.findByIdOrThrow(masterId)
    val item = itemRepository.findByIdOrNull(itemId) ?: return null

    val alreadyOffered = master.container.reservedSlots(tradeId).map { it.id }.toSet()

    when {
      uniqueId != 0L -> master.container.reserveInstance(uniqueId, tradeId)
      !item.stackable -> master.container.reserveAnyInstance(itemId, tradeId)
      else -> master.container.reserveStackable(itemId, amount, tradeId)
    } ?: return null

    val saved = masterRepository.saveAndFlush(master)

    val persisted = saved.container.reservedSlots(tradeId).firstOrNull { it.id !in alreadyOffered }
    if (persisted == null) {
      LOG.warn { "Trade $tradeId: reservation of item $itemId for master $masterId did not survive the flush" }
      return null
    }

    return ReservedItem.of(persisted)
  }

  /**
   * Gives one promised line back to its owner.
   *
   * @return what came back, so the caller can put it into the live inventory, or null when the line is not
   *   promised to this trade any more
   */
  @Transactional
  fun releaseTradeReservation(masterId: Long, tradeId: Long, offerSlotId: Long): ReservedItem? {
    val master = masterRepository.findByIdOrThrow(masterId)
    val slot = master.container.slots
      .firstOrNull { it.id == offerSlotId && it.reservedByTradeId == tradeId }
      ?: return null

    // Read the line before releasing it: releasing may merge the stack away, and the merged-into slot is a
    // different row carrying a different amount.
    val released = ReservedItem.of(slot)

    master.container.releaseReservation(offerSlotId, tradeId)
    masterRepository.save(master)

    return released
  }

  /**
   * Gives everything one master promised to [tradeId] back, for a trade that ended without an exchange.
   *
   * Answers what came back rather than nothing, because the live inventory has to be put back together too
   * and only this transaction knows what was in there.
   */
  @Transactional
  fun releaseAllTradeReservations(masterId: Long, tradeId: Long): List<ReservedItem> {
    val master = masterRepository.findByIdOrThrow(masterId)
    val released = master.container.reservedSlots(tradeId).map { ReservedItem.of(it) }

    if (released.isEmpty()) {
      return emptyList()
    }

    master.container.releaseAllReservations(tradeId)
    masterRepository.save(master)

    return released
  }

  /**
   * **The atomic point of a trade.** Moves everything each side promised to the other, in one transaction, or
   * moves nothing at all.
   *
   * All-or-nothing for the same reason [consumeAll] is, only with more at stake: a half-completed exchange
   * would take one player's goods and hand back nothing. The verification and both moves share a transaction,
   * so a concurrent drop or craft can only land before or after, never between them.
   *
   * The reserved slots are re-read here and checked against what the trade session recorded rather than
   * trusted from the caller: settlement happens a message or two after the offers were made. A mismatch
   * throws, which is what rolls the whole thing back.
   *
   * [ItemInstance] rows are moved, not recreated, so a well-forged sword changes hands complete - with its
   * wear, its rune slots, its upgrade level and the master who made it.
   */
  @Transactional
  fun settleTrade(
    tradeId: Long,
    masterAId: Long,
    masterBId: Long,
    expectedA: Set<Long>,
    expectedB: Set<Long>,
  ): Settlement {
    require(masterAId != masterBId) { "A master cannot trade with themselves (master $masterAId)" }

    val masterA = masterRepository.findByIdOrThrow(masterAId)
    val masterB = masterRepository.findByIdOrThrow(masterBId)

    val heldA = masterA.container.reservedSlots(tradeId).map { it.id }.toSet()
    val heldB = masterB.container.reservedSlots(tradeId).map { it.id }.toSet()

    if (heldA != expectedA || heldB != expectedB) {
      throw TradeSettlementFailedException(
        tradeId,
        "reserved slots moved: master $masterAId holds $heldA (expected $expectedA), " +
                "master $masterBId holds $heldB (expected $expectedB)"
      )
    }

    val toB = move(tradeId, expectedA, from = masterA.container, to = masterB.container)
    val toA = move(tradeId, expectedB, from = masterB.container, to = masterA.container)

    masterRepository.save(masterA)
    masterRepository.save(masterB)

    return Settlement(toMasterA = toA, toMasterB = toB)
  }

  private fun move(
    tradeId: Long,
    slotIds: Set<Long>,
    from: ItemContainer,
    to: ItemContainer,
  ): List<ReservedItem> = slotIds.map { slotId ->
    val detached = from.detachReserved(slotId, tradeId)
      ?: throw TradeSettlementFailedException(tradeId, "slot $slotId vanished mid-settlement")

    if (detached.instance != null) {
      to.addInstance(detached.instance)
    } else {
      to.addStackable(detached.template, detached.amount)
    }

    ReservedItem(
      offerSlotId = slotId,
      itemId = detached.template.id,
      amount = detached.amount,
      weight = detached.template.weight,
      uniqueId = detached.instance?.id ?: 0L,
      stackable = detached.instance == null,
      durability = detached.instance?.durability ?: 0,
      maxDurability = detached.instance?.maxDurability ?: 0,
      slots = detached.instance?.slots ?: 0,
      upgradeLevel = detached.instance?.upgradeLevel ?: 0,
    )
  }

  /** What each side ends up receiving, so both live inventories can be rebuilt after the commit. */
  data class Settlement(
    val toMasterA: List<ReservedItem>,
    val toMasterB: List<ReservedItem>,
  )

  private fun <T> withOwnerContainer(masterId: Long, playerBestiaId: PlayerBestiaId?, block: (ItemContainer) -> T): T {
    return if (playerBestiaId == null) {
      val master = masterRepository.findByIdOrThrow(masterId)
      val result = block(master.container)
      masterRepository.save(master)
      result
    } else {
      val playerBestia = playerBestiaRepository.findByIdOrThrow(playerBestiaId)
      val result = block(playerBestia.container)
      playerBestiaRepository.save(playerBestia)
      result
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }

  private fun grant(container: ItemContainer, item: Item, amount: Int, uniqueId: Long) {
    require(amount > 0) { "amount > 0 required, was $amount" }
    when {
      uniqueId != 0L -> {
        // Re-attach an instance that already exists (e.g. it was lying on the ground).
        container.addInstance(itemInstanceRepository.findByIdOrThrow(uniqueId))
      }

      item.stackable -> container.addStackable(item, amount)

      else -> repeat(amount) {
        container.addInstance(itemInstanceRepository.save(ItemInstance(item = item)))
      }
    }
  }
}
