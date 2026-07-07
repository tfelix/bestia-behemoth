package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.ecs2.World
import net.bestia.zone.item.script.ItemScriptExecutionService
import net.bestia.zone.message.processor.InMessageProcessor
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class UseItemHandler(
  private val itemScriptExecutionService: ItemScriptExecutionService,
  private val itemRepository: ItemRepository,
  private val connectionInfoService: ConnectionInfoService,
  private val world: World
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
    world.modify(activeEntityId) { id ->
      val inventory = world.get(id, Inventory::class)

      if (inventory == null) {
        LOG.warn { "Entity $activeEntityId had no Inventory component but tried to use item" }
        return@modify
      }

      // Verify that the inventory contains the item at least once
      if (!inventory.hasItem(msg.itemId.toInt())) {
        LOG.warn { "Entity $activeEntityId owned no item ${msg.itemId}" }
        return@modify
      }

      LOG.debug { "Item ${msg.itemId} found in inventory for entity $activeEntityId" }

      itemScriptExecutionService.useItem(world, id, item)
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
