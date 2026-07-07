package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.master.GetMasterCMSG
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.message.processor.OutMessageProcessor
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetMasterHandler(
  private val outMessageProcessor: OutMessageProcessor,
  private val availableMasterResolver: AvailableMasterResolver
) : InMessageProcessor.IncomingMessageHandler<GetMasterCMSG> {
  override val handles = GetMasterCMSG::class

  @Transactional(readOnly = true)
  override fun handle(msg: GetMasterCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val availableMasterInfo = availableMasterResolver.getAvailableMaster(msg.playerId)

    outMessageProcessor.sendToPlayer(msg.playerId, availableMasterInfo)

    return true
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
