package net.bestia.zone.postal.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.postal.LetterService
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

@Component
class DeleteLetterHandler(
  private val letterService: LetterService,
  private val connectionInfoService: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<DeleteLetterCMSG> {

  override val wire = decoder(MessageCase.DELETE_LETTER) { accountId, envelope ->
    DeleteLetterCMSG(accountId, envelope.deleteLetter.letterId)
  }

  override fun handle(msg: DeleteLetterCMSG): Boolean {
    val masterId = connectionInfoService.getMasterId(msg.playerId)
    letterService.delete(masterId, msg.letterId)
    outMessageProcessor.sendToPlayer(msg.playerId, LettersSMSG(letterService.mailbox(masterId)))

    return true
  }
}
