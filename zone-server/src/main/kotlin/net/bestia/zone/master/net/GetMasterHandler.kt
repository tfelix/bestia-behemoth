package net.bestia.zone.master.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import net.bestia.zone.master.AvailableMasterResolver

@Component
class GetMasterHandler(
  private val outMessageProcessor: OutMessageProcessor,
  private val availableMasterResolver: AvailableMasterResolver
) : IoMessageHandler<GetMasterCMSG> {
  override val wire = decoder(MessageCase.GET_MASTER) { accountId, _ -> GetMasterCMSG(accountId) }

  @Transactional(readOnly = true)
  override fun handle(msg: GetMasterCMSG): Boolean {
    val availableMasterInfo = availableMasterResolver.getAvailableMaster(msg.playerId)

    outMessageProcessor.sendToPlayer(msg.playerId, availableMasterInfo)

    return true
  }
}
