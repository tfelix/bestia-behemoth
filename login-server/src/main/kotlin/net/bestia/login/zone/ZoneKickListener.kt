package net.bestia.login.zone

import net.bestia.login.account.AccountSessionsTerminatedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Kicks an account from the zones once its sessions are ended. After the commit: a kick the database then rolls
 * back would leave the player kicked with their tokens still valid, free to log straight back in.
 */
@Component
class ZoneKickListener(
  private val kicker: HttpZoneKicker
) {

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  fun onSessionsTerminated(event: AccountSessionsTerminatedEvent) {
    kicker.kick(event.accountId, event.reason)
  }
}
