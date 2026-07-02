package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.network.IsDirty
import net.bestia.zone.ecs.player.Master as MasterComponent
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
  private val zoneServer: ZoneServer,
) {

  fun addItem(entityId: Long, itemIdentifier: String, amount: Int) {
    val item = itemRepository.findByIdentifierOrThrow(itemIdentifier)

    val accessed = zoneServer.withEntityWriteLock(entityId) { entity ->
      val inventory = entity.get(Inventory::class) ?: run {
        LOG.warn { "Entity $entityId has no Inventory component, cannot add item $itemIdentifier" }
        return@withEntityWriteLock
      }

      inventory.addItem(Inventory.Item(itemId = item.id.toInt(), amount = amount))
      entity.add(IsDirty)
    }

    if (accessed == null) {
      LOG.warn { "Entity $entityId not found, cannot add item $itemIdentifier" }
    }
  }

  /**
   * Adds item directly to a master entity. Ideally there should be no difference and those commends should be unified.
   */
  fun addItem(master: Master, itemIdentifier: String, amount: Int) {
    val item = itemRepository.findByIdentifierOrThrow(itemIdentifier)
    master.inventory.addItem(item, amount)
    // TODO check if entity exists and add the item to its inventory component
    // TODO send message to update the connected entity client
    masterRepository.save(master)
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