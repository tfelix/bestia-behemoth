package net.bestia.zone.item.inventory

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class GetInventoryHandler(
  private val connectionInfoService: ConnectionInfoService,
) : TickMessageHandler<GetInventoryCMSG> {
  override val wire = decoder(MessageCase.GET_INVENTORY) { accountId, _ -> GetInventoryCMSG(accountId) }

  override fun handle(world: World, msg: GetInventoryCMSG): Boolean {
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
