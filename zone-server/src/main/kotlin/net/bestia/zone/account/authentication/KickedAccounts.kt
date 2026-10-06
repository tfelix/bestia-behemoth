package net.bestia.zone.account.authentication

import net.bestia.zone.config.ZoneConfig
import org.springframework.stereotype.Component
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Accounts kicked from this zone a short while ago. A kicked player may still hold a login token that was issued
 * a moment before the kick, so a token older than the kick is refused.
 */
@Component
class KickedAccounts(
  config: ZoneConfig
) {

  private val memoryMillis = config.kickMemorySeconds * MILLIS_PER_SECOND
  private val kickedAt = ConcurrentHashMap<Long, Long>()

  fun remember(accountId: Long) {
    val now = System.currentTimeMillis()
    kickedAt.values.removeIf { it < now - memoryMillis }
    kickedAt[accountId] = now
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
