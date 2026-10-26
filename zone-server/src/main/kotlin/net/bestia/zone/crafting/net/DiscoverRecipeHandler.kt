package net.bestia.zone.crafting.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.crafting.discovery.DiscoveryService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/** Tries a mix for whichever entity the player controls, with the knowledge of the master it belongs to. */
@Component
class DiscoverRecipeHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val discoveryService: DiscoveryService,
  private val deadActionGuard: DeadActionGuard,
) : TickMessageHandler<DiscoverRecipeCMSG> {
  override val wire = decoder(MessageCase.DISCOVER_RECIPE) { accountId, envelope ->
    DiscoverRecipeCMSG.fromBnet(accountId, envelope.discoverRecipe)
  }

  override fun handle(world: World, msg: DiscoverRecipeCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)
    if (deadActionGuard.refuses(world, activeEntityId, "discover a recipe")) {
      return true
    }

    discoveryService.discover(
      world = world,
      crafterId = activeEntityId,
      masterEntityId = connectionInfoService.getSelectedMasterEntityId(msg.playerId),
      skillId = msg.skillId,
      materials = msg.materials,
    )

    return true
  }
}
