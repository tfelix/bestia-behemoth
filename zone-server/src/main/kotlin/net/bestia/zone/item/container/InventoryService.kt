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
