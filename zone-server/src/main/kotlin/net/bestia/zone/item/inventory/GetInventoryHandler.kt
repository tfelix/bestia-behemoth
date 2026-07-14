package net.bestia.zone.item.inventory

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.message.InMessageProcessor
import org.springframework.stereotype.Component

@Component
class GetInventoryHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView
) : InMessageProcessor.IncomingMessageHandler<GetInventoryCMSG> {
  override val handles = GetInventoryCMSG::class

  override fun handle(msg: GetInventoryCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    // Get the currently selected entity for this player
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    // Access the entity and force its inventory to resync to the client if present. Nothing
    // changed, so the component isn't dirty on its own - markDirty() requests the resend.
    world.modify(activeEntityId) { id ->
      val inventory = get(id, Inventory::class)

      if (inventory != null) {
        inventory.markDirty()
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
