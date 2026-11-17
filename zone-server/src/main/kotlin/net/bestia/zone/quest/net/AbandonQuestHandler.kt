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
class AbandonQuestHandler(
  private val questService: QuestService,
  private val connectionInfoService: ConnectionInfoService,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<AbandonQuestCMSG> {

  override val wire = decoder(MessageCase.ABANDON_QUEST) { accountId, envelope ->
    AbandonQuestCMSG(accountId, envelope.abandonQuest.questId)
  }

  override fun handle(msg: AbandonQuestCMSG): Boolean {
    val masterId = connectionInfoService.getMasterId(msg.playerId)

    // The client only offers this for quests it may drop, so a refusal means a stale or hand-made request.
    val reply = if (questService.abandon(masterId, msg.questId)) {
      QuestUpdatedSMSG(msg.questId, quest = null)
    } else {
      OperationErrorSMSG(OpError.REQUEST_REFUSED)
    }
    outMessageProcessor.sendToPlayer(msg.playerId, reply)

    return true
  }
}
