package net.bestia.login.zone

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bus.contract.KickOrder
import net.bestia.bus.valkey.ValkeyStreamAppender
import net.bestia.login.account.AccountSessionsTerminatedEvent
import net.bestia.login.metrics.LoginMetrics
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Kicks an account from the zones once its sessions are ended, by appending a kick order to the stream every
 * zone node reads. After the commit: a kick the database then rolls back would leave the player kicked with
 * their tokens still valid, free to log straight back in.
 *
 * Best effort: when Valkey cannot take the order it is logged and lost. The player then keeps playing until
 * they disconnect, but cannot log in again, because their tokens are already gone.
 */
@Component
class ZoneKickListener(
  private val kickOrders: ValkeyStreamAppender<KickOrder>,
  private val metrics: LoginMetrics = LoginMetrics()
) {

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  fun onSessionsTerminated(event: AccountSessionsTerminatedEvent) {
    // Stamped here, after the commit, so it is later than every token issued before the sessions were ended.
    val order = KickOrder(event.accountId, event.reason.name, System.currentTimeMillis())

    val startedAt = System.nanoTime()

    try {
      kickOrders.append(order)
      metrics.kickPublished(event.reason, succeeded = true, elapsedNanos = System.nanoTime() - startedAt)
    } catch (e: Exception) {
      metrics.kickPublished(event.reason, succeeded = false, elapsedNanos = System.nanoTime() - startedAt)
      LOG.error(e) { "Could not tell the zones to kick account ${event.accountId} (${event.reason})" }
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
