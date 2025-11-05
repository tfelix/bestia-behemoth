package net.bestia.zone.item

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.processor.InMessageProcessor
import org.springframework.stereotype.Component

@Component
class UseItemHandler : InMessageProcessor.IncomingMessageHandler<UseItemCMSG> {
  override val handles = UseItemCMSG::class

  override fun handle(msg: UseItemCMSG): Boolean {
    LOG.trace { "RX: $msg" }
    // TODO: Implement item usage logic
    TODO("Implement UseItem handling")
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

