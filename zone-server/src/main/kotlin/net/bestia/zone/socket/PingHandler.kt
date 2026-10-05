package net.bestia.zone.socket

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class PingHandler(
  private val outMessageProcessor: OutMessageProcessor
) : TickMessageHandler<PingCMSG> {
  override val wire = decoder(MessageCase.PING) { accountId, _ -> PingCMSG(accountId) }

  override fun handle(world: World, msg: PingCMSG): Boolean {
    outMessageProcessor.sendToPlayer(msg.playerId, PongSMSG)

    return true
  }
}