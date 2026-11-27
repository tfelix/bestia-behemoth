package net.bestia.api.auth

import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Accounts the login server kicked a short while ago: banned, kicked by a GM, or recovered. Their companion tokens
 * issued before the kick are refused, though still signed and unexpired. A kick is kept a little longer than a
 * companion token lives, so every token it has to refuse expires before it is forgotten.
 *
 * The kick time is the login server's clock, as the token's issue time is, so the two compare on one clock.
 */
@Component
class KickedAccounts(properties: CompanionProperties) {

  val memory: Duration = Duration.ofMinutes(properties.kickMemoryMinutes)

  private val kickedAt = ConcurrentHashMap<Long, Instant>()

  fun remember(accountId: Long, kickedAt: Instant) {
    val oldest = Instant.now().minus(memory)
    this.kickedAt.values.removeIf { it.isBefore(oldest) }
    if (kickedAt.isBefore(oldest)) {
      return
    }

    // Two kicks read out of order: the later one is the one a token has to be younger than.
    this.kickedAt.merge(accountId, kickedAt, ::maxOf)
  }

  fun refuses(accountId: Long, issuedAt: Instant): Boolean {
    val kicked = kickedAt[accountId] ?: return false

    // A token's issue time has whole seconds only, so one from the second of the kick counts as older.
    return !issuedAt.isAfter(kicked)
  }
}
