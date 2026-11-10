package net.bestia.zone.fishing.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.fishing.FishingService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

@Component
class CastLineHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val fishingService: FishingService,
  private val outMessageProcessor: OutMessageProcessor,
) : TickMessageHandler<CastLineCMSG> {
  override val wire = decoder(MessageCase.CAST_LINE) { accountId, envelope ->
    CastLineCMSG.fromBnet(accountId, envelope.castLine)
  }

  override fun handle(world: World, msg: CastLineCMSG): Boolean {
    val anglerId = connectionInfoService.getActiveEntityId(msg.playerId)
    val refusal = fishingService.cast(world, anglerId, msg.target)
    if (refusal != null) {
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(refusal))
    }

    return true
  }
}
