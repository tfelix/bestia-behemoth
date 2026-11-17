package net.bestia.zone.quest.net

import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.quest.QuestService
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/** Sends the played master's whole quest log, once its client has learned which master it plays. */
@Component
class QuestLogPublisher(
  private val questService: QuestService,
  private val connections: ConnectionInfoService,
  private val out: OutMessageProcessor,
) {

  fun sendTo(accountId: Long) {
    val masterId = connections.activeMasterIdOf(accountId) ?: return
    out.sendToPlayer(accountId, QuestLogSMSG(questService.logOf(masterId)))
  }
}
