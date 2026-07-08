package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.processor.InMessageProcessor
import org.springframework.stereotype.Component

@Component
class GetInventoryHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val world: World
) : InMessageProcessor.IncomingMessageHandler<GetInventoryCMSG> {
  override val handles = GetInventoryCMSG::class

  override fun handle(msg: GetInventoryCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    // Get the currently selected entity for this player
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    // Access the entity and mark its inventory for a resync to the client if present
    world.modify(activeEntityId) { id ->
      val inventory = world.get(id, Inventory::class)

      if (inventory != null) {
        world.markChanged<Inventory>(id)
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
