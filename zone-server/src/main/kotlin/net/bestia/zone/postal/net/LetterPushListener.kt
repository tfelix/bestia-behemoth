package net.bestia.zone.postal.net

import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.postal.LetterArrivedEvent
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** Brings a letter to the game at once, while its recipient is the master being played. Otherwise it waits in the mailbox. */
@Component
class LetterPushListener(
  private val connections: ConnectionInfoService,
  private val out: OutMessageProcessor,
) {

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  fun onArrived(event: LetterArrivedEvent) {
    if (!connections.hasActiveSession(event.recipientAccountId)) return
    if (connections.activeMasterIdOf(event.recipientAccountId) != event.recipientMasterId) return

    out.sendToPlayer(event.recipientAccountId, LetterArrivedSMSG(event.letter))
  }
}
