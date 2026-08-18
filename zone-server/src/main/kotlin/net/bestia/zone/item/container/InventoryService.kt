package net.bestia.zone.item.container

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
   * Removes one item (identified by its template id) from a master's container, preferring a unique
   * instance so its identity is preserved for whatever happens next (e.g. dropping it to the
   * ground). The removal must be durable before the caller mutates the in-memory/ECS state, to
   * avoid item duplication on a crash. The returned instance - if any - is intentionally kept alive
   * in the DB, just detached from the container. Returns null if the item was not present.
   */
  @Transactional
  fun removeOneFromMaster(masterId: Long, itemId: Long, amount: Int): ItemContainer.RemovedItem? {
    val master = masterRepository.findByIdOrThrow(masterId)
    val removed = master.container.removeOne(itemId, amount) ?: return null
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
   * which includes holding it *worn*, because every craft that changes an item refuses a worn one.
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
      .firstOrNull { it.uniqueId == uniqueId && !it.isEquipped }
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
