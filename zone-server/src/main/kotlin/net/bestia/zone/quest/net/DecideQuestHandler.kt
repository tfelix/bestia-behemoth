package net.bestia.zone.quest.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.quest.QuestService
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

@Component
class DecideQuestHandler(
  private val questService: QuestService,
  private val connectionInfoService: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<DecideQuestCMSG> {

  override val wire = decoder(MessageCase.DECIDE_QUEST) { accountId, envelope ->
    DecideQuestCMSG(accountId, envelope.decideQuest.questId, envelope.decideQuest.choiceId)
  }

  override fun handle(msg: DecideQuestCMSG): Boolean {
    val masterId = connectionInfoService.getMasterId(msg.playerId)

    // A taken pick reaches the client as a QuestUpdatedSMSG, like every change to the log.
    questService.decide(masterId, msg.questId, msg.choiceId)?.let { refusal ->
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(refusal))
    }

    return true
  }
}
