package net.bestia.zone.item.container

import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.item.Item
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.findByIdentifierOrThrow
import net.bestia.zone.item.instance.ItemInstance
import net.bestia.zone.item.instance.ItemInstanceRepository
import net.bestia.zone.item.instance.findByIdOrThrow
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
  private val itemRepository: ItemRepository,
  private val itemInstanceRepository: ItemInstanceRepository,
) {

  /**
   * Adds an item directly to a managed master's container (e.g. during DB seeding). Does not touch
   * any ECS inventory component.
   */
  @Transactional
  fun addItem(master: Master, itemIdentifier: String, amount: Int) {
    val item = itemRepository.findByIdentifierOrThrow(itemIdentifier)
    grant(master.container, item, amount, uniqueId = 0L)
    masterRepository.save(master)
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
