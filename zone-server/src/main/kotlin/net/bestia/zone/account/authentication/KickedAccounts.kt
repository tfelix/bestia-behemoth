package net.bestia.zone.account.authentication

import net.bestia.zone.config.ZoneConfig
import org.springframework.stereotype.Component
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Accounts kicked from this zone a short while ago. A kicked player may still hold a login token that was issued
 * a moment before the kick, so a token older than the kick is refused.
 *
 * The kick time is the login server's, as the token's issue time is, so the two compare on one clock.
 */
@Component
class KickedAccounts(
  config: ZoneConfig
) {

  private val memoryMillis = config.kickMemorySeconds * MILLIS_PER_SECOND
  private val kickedAt = ConcurrentHashMap<Long, Long>()

  /** Records a kick made at [kickedAtMillis], unless it is already older than this zone remembers kicks. */
  fun remember(accountId: Long, kickedAtMillis: Long) {
    val oldest = System.currentTimeMillis() - memoryMillis
    kickedAt.values.removeIf { it < oldest }
    if (kickedAtMillis < oldest) {
      return
    }

    // Two kicks read out of order: the later one is the one a token has to be younger than.
    kickedAt.merge(accountId, kickedAtMillis, ::maxOf)
  }

  /** Whether a login token for [accountId] that was issued at [issuedAt] predates its kick. */
  fun refuses(accountId: Long, issuedAt: Date): Boolean {
    val kicked = kickedAt[accountId] ?: return false
    if (kicked < System.currentTimeMillis() - memoryMillis) {
      return false
    }

    // A token's issue time has whole seconds only, so one from the second of the kick counts as older.
    return issuedAt.time <= kicked
  }

  private companion object {
    const val MILLIS_PER_SECOND = 1000L
  }
}
