package net.bestia.zone.postal.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.postal.LetterService
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

@Component
class RequestLettersHandler(
  private val letterService: LetterService,
  private val connectionInfoService: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<RequestLettersCMSG> {

  override val wire = decoder(MessageCase.REQUEST_LETTERS) { accountId, _ -> RequestLettersCMSG(accountId) }

  override fun handle(msg: RequestLettersCMSG): Boolean {
    val masterId = connectionInfoService.getMasterId(msg.playerId)
    outMessageProcessor.sendToPlayer(msg.playerId, LettersSMSG(letterService.mailbox(masterId)))

    return true
  }
}
