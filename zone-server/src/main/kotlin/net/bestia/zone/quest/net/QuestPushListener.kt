package net.bestia.zone.quest.net

import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.quest.QuestChangedEvent
import net.bestia.zone.quest.QuestService
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Sends a changed quest to every holder being played. A quest changes for all its holders at once - another
 * signer's hand-in, the poster withdrawing it, the deadline - so the holders are looked up, not kept.
 */
@Component
class QuestPushListener(
  private val questService: QuestService,
  private val connections: ConnectionInfoService,
  private val out: OutMessageProcessor,
) {

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  fun onChanged(event: QuestChangedEvent) {
    val holders = questService.holdersOf(event.questId)
    val playedHolders = connections.activeMasters().filterValues { it in holders }

    for ((accountId, masterId) in playedHolders) {
      out.sendToPlayer(accountId, QuestUpdatedSMSG(event.questId, questService.viewFor(masterId, event.questId)))
    }
  }
}
