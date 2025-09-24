package net.bestia.zone.message.processor.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.PingCMSG
import net.bestia.zone.message.PongSMSG
import net.bestia.zone.message.processor.OutMessageProcessor
import net.bestia.zone.message.processor.InMessageProcessor
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
