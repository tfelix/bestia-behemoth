package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.script.ItemScriptExecutionService
import net.bestia.zone.message.InMessageProcessor
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class UseItemHandler(
  private val itemScriptExecutionService: ItemScriptExecutionService,
  private val itemRepository: ItemRepository,
  private val connectionInfoService: ConnectionInfoService,
  private val inventoryService: InventoryService,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val world: WorldView
) : InMessageProcessor.IncomingMessageHandler<UseItemCMSG> {
  override val handles = UseItemCMSG::class

  override fun handle(msg: UseItemCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val item = itemRepository.findByIdOrNull(msg.itemId)

    if (item == null) {
      LOG.warn { "Item ${msg.itemId} was not found in the database" }
      return true
    }

    if (item.type != Item.ItemType.USABLE) {
      LOG.warn { "Item ${item.identifier} was not usable but account ${msg.playerId} tried to use it" }
      return true
    }

    // Get the currently selected entity for this player
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    // Access the entity and verify inventory ownership
    val consumed = world.modify(activeEntityId) { id ->
      val inventory = get(id, Inventory::class)

      if (inventory == null) {
        LOG.warn { "Entity $activeEntityId had no Inventory component but tried to use item" }
        return@modify false
      }

      // Verify that the inventory contains the item at least once
      if (!inventory.hasItem(msg.itemId.toInt())) {
        LOG.warn { "Entity $activeEntityId owned no item ${msg.itemId}" }
        return@modify false
      }

      LOG.debug { "Item ${msg.itemId} found in inventory for entity $activeEntityId" }

      // `this` is the full World, valid only within this lock-held scope.
      itemScriptExecutionService.useItem(this, id, item)
    }

    // Persist the durable DB decrement off the tick thread, mirroring the ECS consumption so the
    // two do not drift.
    if (consumed == true) {
      val masterId = connectionInfoService.getMasterId(msg.playerId)
      asyncJobExecutor.submit(key = masterId) {
        inventoryService.removeOneFromMaster(masterId, item.id, 1)
      }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
