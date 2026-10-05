package net.bestia.zone.socket

import kotlin.math.min

/**
 * Token bucket for the messages of one connection: [burst] at once, refilling at [perSecond].
 *
 * Not thread safe, and it does not need to be: each connection has its own, used only by its event loop.
 */
class MessageRateLimiter(
  private val burst: Int,
  private val perSecond: Int,
  private val nanoTime: () -> Long = System::nanoTime
) {

  private var tokens = burst.toDouble()
  private var refilledAt = nanoTime()

  fun tryAcquire(): Boolean {
    val now = nanoTime()
    tokens = min(burst.toDouble(), tokens + (now - refilledAt) / NANOS_PER_SECOND * perSecond)
    refilledAt = now

    if (tokens < 1.0) {
      return false
    }

    tokens -= 1.0
    return true
  }

  private companion object {
    const val NANOS_PER_SECOND = 1_000_000_000.0
  }
}
