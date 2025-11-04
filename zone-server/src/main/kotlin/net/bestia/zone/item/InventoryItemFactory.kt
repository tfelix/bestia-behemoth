package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterRepository
import org.springframework.stereotype.Component

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
) {

  fun addItem(entityId: Long, itemIdentifier: String, amount: Int) {
    val item = itemRepository.findByIdentifierOrThrow(itemIdentifier)
    // check if entity exists
    // check if entity is a player bestia or ingame entity.
    // if it is a player bestia add item as inventory item and to inventory component. if its ingame entity only add to inventory component.
    // if player bestia send message to update the connected entity client
  }

  /**
   * Adds item directly to a master entity.
   */
  fun addItem(master: Master, itemIdentifier: String, amount: Int) {
    val item = itemRepository.findByIdentifierOrThrow(itemIdentifier)
    master.inventory.addItem(item, amount)
    // TODO check if entity exists and add the item to its inventory component
    // TODO send message to update the connected entity client
    masterRepository.save(master)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}