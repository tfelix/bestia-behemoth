package net.bestia.zone.socket

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Component

@Component
class PingHandler(
  private val outMessageProcessor: OutMessageProcessor
) : InMessageProcessor.IncomingMessageHandler<PingCMSG> {
  override val handles = PingCMSG::class

  override fun handle(msg: PingCMSG): Boolean {
    LOG.trace { "RX: $msg" }
    outMessageProcessor.sendToPlayer(msg.playerId, PongSMSG)

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}