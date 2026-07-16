package net.bestia.zone.item.inventory

import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.item.Item
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.findByIdentifierOrThrow
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Adds/removes items directly on a master's durable DB inventory. Deliberately has no dependency
 * on the ECS world: callers that also need the live ECS `Inventory` component updated (granting an
 * item to whichever entity a player currently has active) go through
 * [net.bestia.zone.ecs.item.ObtainItemIntent] instead, which mutates the ECS side synchronously on
 * the tick thread and hands this class's DB write off to
 * [net.bestia.zone.ecs.core.AsyncJobExecutor] - see `ObtainItemIntentSystem`.
 * This class does not check any boundary conditions like weight capacity or total item count.
 * You need to use the service for this.
 */
@Component
class InventoryItemFactory(
  private val itemRepository: ItemRepository,
  private val masterRepository: MasterRepository,
) {

  /**
   * Adds an item directly to a master entity that has no live ECS entity yet (e.g. during
   * DB seeding/bootstrapping). Does not touch any ECS inventory component.
   */
  fun addItem(master: Master, itemIdentifier: String, amount: Int) {
    val item = itemRepository.findByIdentifierOrThrow(itemIdentifier)
    master.inventory.addItem(master, item, amount)
    masterRepository.save(master)
  }

  /**
   * Adds an item directly to a master's DB inventory (by masterId) and saves immediately.
   * Used for critical item transactions (e.g. looting) where the corresponding ECS/ground
   * entity has already been removed first to avoid item duplication.
   */
  @Transactional
  fun addItemToMaster(masterId: Long, itemIdentifier: String, amount: Int): Item {
    val item = itemRepository.findByIdentifierOrThrow(itemIdentifier)
    val master = masterRepository.findByIdOrThrow(masterId)

    master.inventory.addItem(master, item, amount)
    masterRepository.save(master)

    return item
  }

  /**
   * Removes an item directly from a master's DB inventory and saves immediately. Used for
   * critical item transactions (e.g. dropping items) where the removal must be durable before
   * the corresponding ECS/in-memory state is mutated, to avoid item duplication on a crash.
   */
  @Transactional
  fun removeItem(masterId: Long, itemIdentifier: String, amount: Int): Boolean {
    val master = masterRepository.findByIdOrThrow(masterId)
    val removed = master.inventory.removeItem(itemIdentifier, amount)

    if (removed) {
      masterRepository.save(master)
    }

    return removed
  }
}
