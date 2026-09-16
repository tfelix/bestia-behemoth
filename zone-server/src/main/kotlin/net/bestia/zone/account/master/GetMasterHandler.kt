package net.bestia.zone.account.master

import net.bestia.zone.account.master.GetMasterCMSG
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OutMessageProcessor
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
    val availableMasterInfo = availableMasterResolver.getAvailableMaster(msg.playerId)

    outMessageProcessor.sendToPlayer(msg.playerId, availableMasterInfo)

    return true
  }
}
