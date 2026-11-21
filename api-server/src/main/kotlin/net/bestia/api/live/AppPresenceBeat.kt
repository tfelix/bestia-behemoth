package net.bestia.api.live

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bus.AppPresence
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Tells the zones which accounts have an app open on this instance, so a whisper to a player away from the game can
 * still arrive. A mark outlives its socket by at most [TTL_SECONDS]; a whisper in that gap waits on the event stream,
 * where the app finds it when it reconnects.
 */
@Component
class AppPresenceBeat(
  private val sessions: LiveSessions,
  private val presence: AppPresence,
) {

  /** Marked at once rather than at the next beat, so a whisper sent right after the app opened finds it. */
  fun opened(accountId: Long) {
    mark(listOf(accountId))
  }

  @Scheduled(fixedDelay = BEAT_MILLIS, initialDelay = BEAT_MILLIS)
  fun beat() {
    mark(sessions.accountIds())
  }

  private fun mark(accountIds: Collection<Long>) {
    try {
      presence.markOpen(accountIds, TTL_SECONDS)
    } catch (e: Exception) {
      // The marks lapse on their own; the next beat writes them again.
      LOG.warn { "App presence not marked: ${e.message}" }
    }
  }

  private companion object {
    const val BEAT_MILLIS = 10_000L
    const val TTL_SECONDS = 30L
    val LOG = KotlinLogging.logger { }
  }
}
