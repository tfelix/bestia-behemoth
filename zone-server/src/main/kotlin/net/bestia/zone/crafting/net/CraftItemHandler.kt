package net.bestia.zone.crafting.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.modify
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component
import net.bestia.zone.crafting.CraftingService

/**
 * Starts one craft for whichever entity the player currently controls.
 *
 * Validation lives entirely in [CraftingService.start] - this only resolves the acting entity, clears whatever
 * else it was channelling, and reports a refusal.
 */
@Component
class CraftItemHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val craftingService: CraftingService,
  private val castCancelService: CastCancelService,
  private val deadActionGuard: DeadActionGuard,
  private val outMessageProcessor: OutMessageProcessor,
) : TickMessageHandler<CraftItemCMSG> {
  override val wire = decoder(MessageCase.CRAFT_ITEM) { accountId, envelope ->
    CraftItemCMSG.fromBnet(accountId, envelope.craftItem)
  }

  override fun handle(world: World, msg: CraftItemCMSG): Boolean {
    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    if (deadActionGuard.refuses(world, activeEntityId, "craft")) {
      return true
    }

    // A craft and a cast share one progress bar on the client, so starting one has to end the other - see
    // Crafting's own note on why it reuses CastingComponentSMSG.
    castCancelService.cancelCast(world, activeEntityId)

    val denial = world.modify(activeEntityId) { id ->
      craftingService.start(world = this, entityId = id, recipeId = msg.recipeId, targetUniqueId = msg.targetUniqueId)
    }

    if (denial != null) {
      LOG.debug { "Craft of recipe ${msg.recipeId} by $activeEntityId refused: $denial" }
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(denial))
    }

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
