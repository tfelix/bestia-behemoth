package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.ZoneServer
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.network.IsDirty
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.message.processor.InMessageProcessor
import org.springframework.stereotype.Component

@Component
class GetInventoryHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val zoneServer: ZoneServer
) : InMessageProcessor.IncomingMessageHandler<GetInventoryCMSG> {
  override val handles = GetInventoryCMSG::class

  override fun handle(msg: GetInventoryCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    // Get the currently selected entity for this player
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    // Access the entity with a write lock and get the Inventory component if present
    zoneServer.withEntityWriteLock(activeEntityId) { entity ->
      val inventory = entity.get(Inventory::class)

      if (inventory != null) {
        // Mark the inventory as dirty so it will be sent to the client
        inventory.markDirty()
        entity.add(IsDirty)
        LOG.debug { "Marked inventory as dirty for entity $activeEntityId" }
      } else {
        LOG.debug { "Entity $activeEntityId has no inventory component" }
      }
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
