package net.bestia.zone.item.container

import net.bestia.zone.item.stat.ItemStatCatalog
import net.bestia.zone.item.stat.ItemRoll
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.item.ItemProvenance
import net.bestia.zone.item.PlayerItemIds
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.ItemNotFoundException
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.persistence.findByIdentifierOrThrow
import net.bestia.zone.item.persistence.ItemInstance
import net.bestia.zone.item.persistence.ItemInstanceRepository
import net.bestia.zone.item.persistence.findByIdOrThrow
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.item.persistence.ContainerSlot

/**
 * The single durable-inventory entry point. Adds/removes items on an owner's [ItemContainer] and
 * owns the rule for when a plain stackable pile vs. a unique [ItemInstance] is created - callers
 * (message handlers, ECS systems, seeding) never touch [net.bestia.zone.item.persistence.ContainerSlot]/[ItemInstance] directly.
 *
 * Deliberately has no dependency on the ECS world: callers that also need the live ECS `Inventory`
 * component updated go through [net.bestia.zone.item.ecs.ObtainItemIntent] instead, which mutates
 * the ECS side on the tick thread and hands the DB write here off to
 * [net.bestia.zone.persistence.AsyncJobExecutor]. This class does not check carry weight or item
 * count limits - the caller/service is responsible for that.
 */
@Service
class InventoryService(
  private val owners: ContainerOwners,
  private val itemRepository: ItemRepository,
  private val itemInstanceRepository: ItemInstanceRepository,
  private val itemStats: ItemStatCatalog,
) {

  /**
   * Adds an item directly to a master's container (e.g. during DB seeding). Does not touch any ECS
   * inventory component.
   *
   * Takes the id rather than a `Master`: a caller may reuse one instance across several of these
   * calls, each its own transaction. Since `save()` on an already-persisted entity goes through
   * `merge()` - which returns a new managed copy rather than updating the argument in place - a
   * reused, once-already-saved instance would still show its previously-added slots with `id == 0`,
   * and cascading persist would re-insert them as new rows on every subsequent call.
   */
  @Transactional
  fun addItem(masterId: Long, itemIdentifier: String, amount: Int) {
    val master = owners.lockedMaster(masterId)
    val item = itemRepository.findByIdentifierOrThrow(itemIdentifier)
    grant(master.container, item, amount, uniqueId = 0L)
    owners.save(master)
  }

  /**
   * Grants items to a master's own container and saves immediately. Used for critical item
   * transactions (e.g. looting) where the corresponding ECS/ground entity has already been removed
   * first to avoid duplication. Pass [uniqueId] != 0 to re-attach an existing instance that was
   * lying on the ground; pass 0 for a fresh grant, and whether it becomes a stack or a new instance
   * is decided from the item template. [freshInstanceIds] are the ids the live inventory already shows for
   * the new copies; without them the copies get new ids here.
   */
  @Transactional
  fun grantToMaster(
    masterId: Long,
    itemId: Long,
    amount: Int,
    uniqueId: Long = 0L,
    roll: ItemRoll? = null,
    provenance: ItemProvenance? = null,
    freshInstanceIds: List<Long> = emptyList()
  ) {
    grantToOwner(masterId, null, itemId, amount, uniqueId, roll, provenance, freshInstanceIds)
  }

  /** [grantToMaster] for whichever entity holds the item: the master when [playerBestiaId] is null. */
  @Transactional
  fun grantToOwner(
    masterId: Long,
    playerBestiaId: PlayerBestiaId?,
    itemId: Long,
    amount: Int,
    uniqueId: Long = 0L,
    roll: ItemRoll? = null,
    provenance: ItemProvenance? = null,
    freshInstanceIds: List<Long> = emptyList()
  ) {
    withOwnerContainer(masterId, playerBestiaId) { container ->
      val item = itemRepository.findByIdOrNull(itemId) ?: throw ItemNotFoundException(itemId.toString())
      grant(container, item, amount, uniqueId, roll, provenance, freshInstanceIds)
    }
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
    val master = owners.lockedMaster(masterId)
    val instance = itemInstanceRepository.save(ItemInstance(item = item))

    master.container.addInstance(instance)
    owners.save(master)

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
    return removeOneFromOwner(masterId, null, itemId, amount, uniqueId)
  }

  /** [removeOneFromMaster] for whichever entity holds the item: the master when [playerBestiaId] is null. */
  @Transactional
  fun removeOneFromOwner(
    masterId: Long,
    playerBestiaId: PlayerBestiaId?,
    itemId: Long,
    amount: Int,
    uniqueId: Long = 0L
  ): ItemContainer.RemovedItem? {
    return withOwnerContainer(masterId, playerBestiaId) { container -> container.removeOne(itemId, amount, uniqueId) }
  }

  /**
   * Removes what the player named in their live inventory: the instance [uniqueId], or [amount] off the pile of
   * [itemId] made by [provenance]. See [ItemContainer.removeNamed].
   */
  @Transactional
  fun removeNamedFromOwner(
    masterId: Long,
    playerBestiaId: PlayerBestiaId?,
    itemId: Long,
    amount: Int,
    uniqueId: Long,
    provenance: ItemProvenance?
  ): ItemContainer.RemovedItem? {
    return withOwnerContainer(masterId, playerBestiaId) { it.removeNamed(itemId, amount, uniqueId, provenance) }
  }

  /**
   * Takes every one of [piles] off the owner's container, or nothing at all: what a discovery attempt spends. Each
   * pile is named by its item and maker, as the live inventory showed it to the player.
   */
  @Transactional
  fun takeNamedPiles(masterId: Long, playerBestiaId: PlayerBestiaId?, piles: List<NamedPile>): Boolean {
    return withOwnerContainer(masterId, playerBestiaId) { container ->
      val held = piles.all { container.pileAmount(it.itemId, it.provenance) >= it.amount }
      if (held) {
        piles.forEach { container.removeNamed(it.itemId, it.amount, uniqueId = 0L, provenance = it.provenance) }
      }
      held
    }
  }

  data class NamedPile(val itemId: Long, val provenance: ItemProvenance?, val amount: Int)

  /** Empties an owner's bag of everything not worn or promised, and answers what left. See [ItemContainer.removeAllFree]. */
  @Transactional
  fun removeAllFreeFromOwner(masterId: Long, playerBestiaId: PlayerBestiaId?): List<ItemContainer.DetachedItem> {
    return withOwnerContainer(masterId, playerBestiaId) { container -> container.removeAllFree() }
  }

  /**
   * Durably marks an item as worn in [slot] by the given owner - a master when
   * [playerBestiaId] is null, otherwise that player bestia. The ECS
   * [net.bestia.zone.item.ecs.Equipment] component has already been mutated by the caller on the
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

  /**
   * [equip] for stackable gear: one copy leaves the pile as the instance [instanceId], the id the live
   * inventory already shows for it. See [ItemContainer.equipFromPile].
   */
  @Transactional
  fun equipFromPile(
    masterId: Long,
    playerBestiaId: PlayerBestiaId?,
    itemId: Long,
    provenance: ItemProvenance?,
    instanceId: Long,
    slot: EquipmentSlot
  ): Boolean {
    val item = itemRepository.findByIdOrNull(itemId) ?: throw ItemNotFoundException(itemId.toString())

    return withOwnerContainer(masterId, playerBestiaId) { container ->
      container.equipFromPile(item, provenance, slot) {
        itemInstanceRepository.save(ItemInstance(item = item, provenance = provenance, id = instanceId))
      }
    }
  }

  /**
   * Takes off what is worn in [slot]. A copy of stackable gear that is still unused goes back into its pile, so
   * a torch carried through a day does not stay a loose copy. `EquipmentChanges.takeOff` folds the live bag the
   * same way.
   */
  @Transactional
  fun unequip(masterId: Long, playerBestiaId: PlayerBestiaId?, slot: EquipmentSlot): Boolean {
    var folded: ItemInstance? = null
    val takenOff = withOwnerContainer(masterId, playerBestiaId) { container ->
      val taken = container.unequip(slot) ?: return@withOwnerContainer false
      folded = foldBackIntoPile(container, taken)
      true
    }
    // After the container is saved, so the row is no longer referenced when it goes.
    folded?.let { itemInstanceRepository.delete(it) }

    return takenOff
  }

  /** Answers the instance that went back into its pile, which the caller deletes, or null when it stays a copy. */
  private fun foldBackIntoPile(container: ItemContainer, taken: ContainerSlot): ItemInstance? {
    val instance = taken.itemInstance?.takeIf { taken.template.stackable && it.isUnused } ?: return null

    container.takeInstance(instance.id)
    container.addStackable(taken.template, 1, instance.provenance)

    return instance
  }

  /**
   * Takes every stack in [inputs] off a master's container (or its bestia's, when [playerBestiaId] is set) in one
   * transaction, or nothing at all.
   *
   * All-or-nothing is the point: a craft that consumed two of its three materials and then found the
   * third missing would have eaten them for no result and no message. The check and the removals share a
   * transaction, so a concurrent drop can only land before or after, never between them.
   *
   * @return false, having changed nothing, when any input is not held in full
   */
  @Transactional
  fun consumeAll(masterId: Long, inputs: List<Pair<Long, Int>>, playerBestiaId: PlayerBestiaId? = null): Boolean {
    if (inputs.isEmpty()) return true

    // Summed per item first, so a recipe naming the same material twice is checked against the total
    // rather than against whichever line happened to be looked at last.
    val required = inputs.groupBy({ it.first }, { it.second }).mapValues { (_, amounts) -> amounts.sum() }

    return withOwnerContainer(masterId, playerBestiaId) { container ->
      if (required.any { (itemId, amount) -> !container.hasItem(itemId, amount) }) {
        return@withOwnerContainer false
      }

      required.forEach { (itemId, amount) ->
        if (!container.removeStackable(itemId, amount)) {
          // Reached only if an input is held solely as instance slots, which `hasItem` counts and
          // `removeStackable` will not touch. Rolling back is the honest answer: the alternative is
          // consuming part of the recipe.
          throw IllegalStateException(
            "Master $masterId holds item $itemId only as unique instances; recipe inputs must be stackable"
          )
        }
      }

      true
    }
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
  fun heldInstance(masterId: Long, uniqueId: Long, playerBestiaId: PlayerBestiaId? = null): ItemInstance? {
    if (uniqueId == 0L) return null

    val container = if (playerBestiaId == null) {
      owners.lockedMaster(masterId).container
    } else {
      owners.lockedPlayerBestia(playerBestiaId).container
    }

    return container.slots
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
  fun updateInstance(
    masterId: Long,
    uniqueId: Long,
    playerBestiaId: PlayerBestiaId? = null,
    change: (ItemInstance) -> Unit
  ): ItemInstance? {
    val instance = heldInstance(masterId, uniqueId, playerBestiaId) ?: return null

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
  fun destroyInstance(masterId: Long, uniqueId: Long, playerBestiaId: PlayerBestiaId? = null): Boolean {
    val taken = withOwnerContainer(masterId, playerBestiaId) { it.takeInstance(uniqueId) } ?: return false

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
  fun reserveForTrade(
    masterId: Long,
    tradeId: Long,
    itemId: Long,
    uniqueId: Long,
    amount: Int,
    provenance: ItemProvenance? = null
  ): ReservedItem? {
    require(amount > 0) { "amount > 0 required, was $amount" }

    val master = owners.lockedMaster(masterId)
    val item = itemRepository.findByIdOrNull(itemId) ?: return null

    val alreadyOffered = master.container.reservedSlots(tradeId).map { it.id }.toSet()

    when {
      uniqueId != 0L -> master.container.reserveInstance(uniqueId, tradeId)
      !item.stackable -> master.container.reserveAnyInstance(itemId, tradeId)
      else -> master.container.reserveStackable(itemId, amount, tradeId, provenance)
    } ?: return null

    val saved = owners.saveAndFlush(master)

    val persisted = saved.container.reservedSlots(tradeId).firstOrNull { it.id !in alreadyOffered }
    if (persisted == null) {
      LOG.warn { "Trade $tradeId: reservation of item $itemId for master $masterId did not survive the flush" }
      return null
    }

    return ReservedItem.of(persisted, itemStats.itemLevelOf(persisted))
  }

  /**
   * Gives one promised line back to its owner.
   *
   * @return what came back, so the caller can put it into the live inventory, or null when the line is not
   *   promised to this trade any more
   */
  @Transactional
  fun releaseTradeReservation(masterId: Long, tradeId: Long, offerSlotId: Long): ReservedItem? {
    val master = owners.lockedMaster(masterId)
    val slot = master.container.slots
      .firstOrNull { it.id == offerSlotId && it.reservedByTradeId == tradeId }
      ?: return null

    // Read the line before releasing it: releasing may merge the stack away, and the merged-into slot is a
    // different row carrying a different amount.
    val released = ReservedItem.of(slot, itemStats.itemLevelOf(slot))

    master.container.releaseReservation(offerSlotId, tradeId)
    owners.save(master)

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
    val master = owners.lockedMaster(masterId)
    val released = master.container.reservedSlots(tradeId).map { ReservedItem.of(it, itemStats.itemLevelOf(it)) }

    if (released.isEmpty()) {
      return emptyList()
    }

    master.container.releaseAllReservations(tradeId)
    owners.save(master)

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

    // Locked in id order, like every other multi-master write, so two settlements cannot deadlock.
    val (masterA, masterB) = if (masterAId < masterBId) {
      owners.lockedMaster(masterAId).let { a -> a to owners.lockedMaster(masterBId) }
    } else {
      owners.lockedMaster(masterBId).let { b -> owners.lockedMaster(masterAId) to b }
    }

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

    owners.save(masterA)
    owners.save(masterB)

    return Settlement(toMasterA = toA, toMasterB = toB)
  }

  /**
   * Moves goods out of a master's inventory into [escrow], a container no player holds - an auction listing's.
   *
   * Reserves first so a partial stack splits by the same rules a trade offer does, then detaches the
   * reservation within this transaction; the marker never reaches the database.
   *
   * @return what moved, or null when it is not freely held in that quantity
   */
  @Transactional
  fun moveToEscrow(
    masterId: Long,
    itemId: Long,
    uniqueId: Long,
    amount: Int,
    escrow: ItemContainer,
    provenance: ItemProvenance? = null
  ): ReservedItem? {
    require(amount > 0) { "amount > 0 required, was $amount" }

    val master = owners.lockedMaster(masterId)
    val item = itemRepository.findByIdOrNull(itemId) ?: return null

    val reserved = when {
      uniqueId != 0L -> master.container.reserveInstance(uniqueId, ESCROW_MARKER)
      !item.stackable -> master.container.reserveAnyInstance(itemId, ESCROW_MARKER)
      else -> master.container.reserveStackable(itemId, amount, ESCROW_MARKER, provenance)
    } ?: return null

    val moved = move(ESCROW_MARKER, setOf(reserved.id), from = master.container, to = escrow).single()
    owners.save(master)

    return moved
  }

  /** Moves everything held in [escrow] to a master. @return what arrived, for the live inventory */
  @Transactional
  fun moveFromEscrow(escrow: ItemContainer, masterId: Long): List<ReservedItem> {
    val master = owners.lockedMaster(masterId)

    val arrived = escrow.removeAllFree().map { detached ->
      add(master.container, detached)
      arrivedAs(slotId = 0L, detached)
    }

    owners.save(master)

    return arrived
  }

  private fun move(
    tradeId: Long,
    slotIds: Set<Long>,
    from: ItemContainer,
    to: ItemContainer,
  ): List<ReservedItem> = slotIds.map { slotId ->
    val detached = from.detachReserved(slotId, tradeId)
      ?: throw TradeSettlementFailedException(tradeId, "slot $slotId vanished mid-settlement")

    add(to, detached)
    arrivedAs(slotId, detached)
  }

  private fun add(to: ItemContainer, detached: ItemContainer.DetachedItem) {
    if (detached.instance != null) {
      to.addInstance(detached.instance)
    } else {
      to.addStackable(detached.template, detached.amount, detached.provenance)
    }
  }

  private fun arrivedAs(slotId: Long, detached: ItemContainer.DetachedItem): ReservedItem {
    return ReservedItem(
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
      itemLevel = detached.instance?.itemLevel
        ?: itemStats.itemLevelOf(detached.template.id, detached.instance?.rolledStats.orEmpty(), detached.instance?.slots ?: 0),
      rolledStats = detached.instance?.rolledStats.orEmpty(),
      provenance = detached.instance?.provenance ?: detached.provenance,
    )
  }

  /** What each side ends up receiving, so both live inventories can be rebuilt after the commit. */
  data class Settlement(
    val toMasterA: List<ReservedItem>,
    val toMasterB: List<ReservedItem>,
  )

  private fun <T> withOwnerContainer(masterId: Long, playerBestiaId: PlayerBestiaId?, block: (ItemContainer) -> T): T {
    return if (playerBestiaId == null) {
      val master = owners.lockedMaster(masterId)
      val result = block(master.container)
      owners.save(master)
      result
    } else {
      val playerBestia = owners.lockedPlayerBestia(playerBestiaId)
      val result = block(playerBestia.container)
      owners.save(playerBestia)
      result
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** Stands in for a trade id while an escrow move splits a stack. Trade ids count up from 1. */
    const val ESCROW_MARKER = Long.MIN_VALUE
  }

  private fun grant(
    container: ItemContainer,
    item: Item,
    amount: Int,
    uniqueId: Long,
    roll: ItemRoll? = null,
    provenance: ItemProvenance? = null,
    freshInstanceIds: List<Long> = emptyList()
  ) {
    require(amount > 0) { "amount > 0 required, was $amount" }
    require(freshInstanceIds.isEmpty() || freshInstanceIds.size == amount) {
      "${freshInstanceIds.size} instance ids for $amount copies of item ${item.id}"
    }

    when {
      uniqueId != 0L -> {
        // Re-attach an instance that already exists (e.g. it was lying on the ground).
        container.addInstance(itemInstanceRepository.findByIdOrThrow(uniqueId))
      }

      item.stackable -> container.addStackable(item, amount, provenance)

      else -> freshInstanceIds.ifEmpty { List(amount) { PlayerItemIds.next() } }.forEach { instanceId ->
        val instance = ItemInstance(
          item = item,
          provenance = provenance,
          slots = roll?.sockets ?: 0,
          itemLevel = roll?.itemLevel,
          id = instanceId
        )
        roll?.let { instance.assignRolledStats(it.stats) }
        container.addInstance(itemInstanceRepository.save(instance))
      }
    }
  }
}
