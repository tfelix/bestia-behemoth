package net.bestia.zone.crafting

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.battle.ecs.skill.CastCancelService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

/**
 * Abandons the craft in progress. Nothing to refund and nothing to report: removing the component is what tells
 * the client the bar is over, which is the same signal a finished craft sends.
 */
@Component
class CancelCraftHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val castCancelService: CastCancelService,
) : TickMessageHandler<CancelCraftCMSG> {
  override val wire = decoder(MessageCase.CANCEL_CRAFT) { accountId, _ -> CancelCraftCMSG(accountId) }

  override fun handle(world: World, msg: CancelCraftCMSG): Boolean {
    castCancelService.cancelCraft(world, connectionInfoService.getActiveEntityId(msg.playerId))

    return true
  }
}
