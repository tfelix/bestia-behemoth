package net.bestia.zone.postal.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.postal.LetterService
import net.bestia.zone.postal.LetterService.Outcome
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.BasicSkillGate
import org.springframework.stereotype.Component

@Component
class SendLetterHandler(
  private val letterService: LetterService,
  private val basicSkillGate: BasicSkillGate,
  private val connectionInfoService: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<SendLetterCMSG> {

  override val wire = decoder(MessageCase.SEND_LETTER) { accountId, envelope ->
    val letter = envelope.sendLetter
    SendLetterCMSG(accountId, letter.recipientName, letter.subject, letter.body)
  }

  override fun handle(msg: SendLetterCMSG): Boolean {
    // A letter is talking to another player, so it opens with chat.
    if (!basicSkillGate.mayChat(msg.playerId)) {
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(OpError.BASIC_SKILL_CHAT_LOCKED))
      return true
    }

    val sender = LetterService.Sender(
      masterId = connectionInfoService.getMasterId(msg.playerId),
      liveEntityId = connectionInfoService.getSelectedMasterEntityId(msg.playerId),
    )

    val answer = when (val outcome = letterService.send(sender, msg.recipientName, msg.subject, msg.body)) {
      is Outcome.Sent -> LetterSentSMSG(outcome.recipientName)
      is Outcome.Refused -> OperationErrorSMSG(outcome.code, outcome.args)
    }
    outMessageProcessor.sendToPlayer(msg.playerId, answer)

    return true
  }
}
