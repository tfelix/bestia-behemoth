package net.bestia.zone.crafting

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.battle.skill.CastCancelService
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.message.InMessageProcessor
import org.springframework.stereotype.Component

/**
 * Abandons the craft in progress. Nothing to refund and nothing to report: removing the component is what tells
 * the client the bar is over, which is the same signal a finished craft sends.
 */
@Component
class CancelCraftHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val castCancelService: CastCancelService,
) : InMessageProcessor.IncomingMessageHandler<CancelCraftCMSG> {
  override val handles = CancelCraftCMSG::class

  override fun handle(msg: CancelCraftCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    castCancelService.cancelCraft(connectionInfoService.getActiveEntityId(msg.playerId))

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
