package net.bestia.zone.crafting

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.skill.CastCancelService
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.logout.LogoutCancelService
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

/**
 * Starts one craft for whichever entity the player currently controls.
 *
 * Validation lives entirely in [CraftingService.start] - this only resolves the acting entity, clears whatever
 * else it was channelling, and reports a refusal.
 */
@Component
class CraftItemHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val world: WorldView,
  private val craftingService: CraftingService,
  private val castCancelService: CastCancelService,
  private val logoutCancelService: LogoutCancelService,
  private val outMessageProcessor: OutMessageProcessor,
) : InMessageProcessor.IncomingMessageHandler<CraftItemCMSG> {
  override val handles = CraftItemCMSG::class

  override fun handle(msg: CraftItemCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val activeEntityId = connectionInfoService.getActiveEntityId(msg.playerId)

    logoutCancelService.cancelLogout(activeEntityId)

    // A craft and a cast share one progress bar on the client, so starting one has to end the other - see
    // Crafting's own note on why it reuses CastingComponentSMSG.
    castCancelService.cancelCast(activeEntityId)

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
