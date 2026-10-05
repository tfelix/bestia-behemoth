package net.bestia.zone.ecs.core

import java.util.concurrent.TimeUnit

/**
 * Lets one log line through per [intervalMillis] and counts the ones it held back, so a fault that
 * repeats every tick still shows up without burying everything else in the log.
 */
class RateLimitedLog(intervalMillis: Long = 1_000L) {
  private val intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMillis)
  private var lastEmitNanos: Long? = null
  private var suppressed = 0

  /** Calls [log] with the number of lines suppressed since the last one, or counts this one. */
  @Synchronized
  fun emit(log: (suppressed: Int) -> Unit) {
    val now = java.lang.System.nanoTime()
    val last = lastEmitNanos

    if (last != null && now - last < intervalNanos) {
      suppressed++
      return
    }

    val held = suppressed
    lastEmitNanos = now
    suppressed = 0
    log(held)
  }
}
