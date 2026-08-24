package net.bestia.login.ratelimit

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * A per-key sliding window, in memory.
 *
 * Deliberately not a distributed limiter. The point is to stop a single host walking the recovery
 * endpoint or hammering the ceremony endpoints, and a per-instance counter does that; the login
 * server runs as one process today, and reaching for Redis to hold counters that reset on restart
 * would be the more expensive kind of wrong.
 *
 * Keys are unbounded in principle, so [sweep] has to run - otherwise an attacker rotating source
 * addresses turns the limiter itself into the memory leak.
 */
@Component
class RateLimiter {

  private val buckets = ConcurrentHashMap<String, AtomicReference<Window>>()

  /** True when the call is allowed. */
  fun tryAcquire(key: String, limit: Int, window: Duration, now: Instant = Instant.now()): Boolean {
    val reference = buckets.computeIfAbsent(key) { AtomicReference(Window(now, 0)) }

    while (true) {
      val current = reference.get()

      val next = if (Duration.between(current.startedAt, now) >= window) {
        Window(now, 1)
      } else {
        Window(current.startedAt, current.count + 1)
      }

      if (next.count > limit) {
        return false
      }

      if (reference.compareAndSet(current, next)) {
        return true
      }
    }
  }

  @Scheduled(fixedDelayString = "PT10M")
  fun sweepStale() {
    sweep(STALE_AFTER)
  }

  fun sweep(olderThan: Duration, now: Instant = Instant.now()) {
    buckets.entries.removeIf { Duration.between(it.value.get().startedAt, now) > olderThan }
  }

  fun reset() {
    buckets.clear()
  }

  private data class Window(
    val startedAt: Instant,
    val count: Int
  )

  companion object {
    private val STALE_AFTER: Duration = Duration.ofMinutes(30)
  }
}
