package net.bestia.zone.notebook.net

import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.notebook.NotebookChangedEvent
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Tells the game of every notebook write, whichever app made it - its own included, which is how it learns they
 * landed. Only while the account plays, and for a master's collection only while that master is the one played.
 */
@Component
class NotebookPushListener(
  private val connections: ConnectionInfoService,
  private val out: OutMessageProcessor,
) {

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  fun onChanged(event: NotebookChangedEvent) {
    if (!connections.hasActiveSession(event.accountId)) return
    if (event.masterId != null && connections.activeMasterIdOf(event.accountId) != event.masterId) return

    out.sendToPlayer(
      event.accountId,
      NotebookChangedSMSG(event.collection, event.key, event.revision, event.deleted, event.body ?: "")
    )
  }
}
