package net.bestia.zone.postal.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.postal.LetterService
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

@Component
class ReadLetterHandler(
  private val letterService: LetterService,
  private val connectionInfoService: ConnectionInfoService,
) : IoMessageHandler<ReadLetterCMSG> {

  override val wire = decoder(MessageCase.READ_LETTER) { accountId, envelope ->
    ReadLetterCMSG(accountId, envelope.readLetter.letterId)
  }

  override fun handle(msg: ReadLetterCMSG): Boolean {
    letterService.markRead(connectionInfoService.getMasterId(msg.playerId), msg.letterId)

    return true
  }
}
