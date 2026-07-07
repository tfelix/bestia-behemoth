package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs2.EntityId
import net.bestia.zone.ecs2.World
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Adds an item to a player inventory. This must check if there is an active connection and the item is
 * added to the active entity, or if there is no connection and no active entity directly to the
 * inventory database.
 * This class does not check any boundary conditions like weight capacity or total item count.
 * You need to use the service for this.
 */
@Component
class InventoryItemFactory(
  private val itemRepository: ItemRepository,
  private val masterRepository: MasterRepository,
  private val world: World,
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
   * Adds an item to a master's DB inventory and mirrors it into the currently active entity's
   * ECS inventory component (master's own entity, or whichever bestia entity the player has
   * selected). This is the DB-first, then-ECS ordering used everywhere items are granted, so
   * that a crash between the two steps never loses the durable DB write.
   */
  @Transactional
  fun addItemToMasterAndEntity(
    masterId: Long,
    activeEntityId: EntityId,
    itemIdentifier: String,
    amount: Int,
    uniqueId: Long = 0
  ) {
    val item = addItemToMaster(masterId, itemIdentifier, amount)

    world.modify(activeEntityId) { id ->
      val inventory = world.get(id, Inventory::class)

      if (inventory == null) {
        LOG.warn { "Entity $activeEntityId has no Inventory component, cannot sync item $itemIdentifier" }
        return@modify
      }

      inventory.addItem(Inventory.Item(itemId = item.id.toInt(), amount = amount, uniqueId = uniqueId))
      world.markChanged<Inventory>(id)
    }
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

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
