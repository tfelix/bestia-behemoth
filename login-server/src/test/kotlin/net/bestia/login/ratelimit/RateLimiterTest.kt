package net.bestia.login.ratelimit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class RateLimiterTest {

  private val limiter = RateLimiter()
  private val window: Duration = Duration.ofMinutes(1)
  private val start: Instant = Instant.parse("2026-08-24T12:00:00Z")

  @Test
  fun `allows up to the limit then refuses`() {
    repeat(3) {
      assertTrue(limiter.tryAcquire("k", 3, window, start))
    }

    assertFalse(limiter.tryAcquire("k", 3, window, start))
  }

  @Test
  fun `starts a fresh window once the old one has elapsed`() {
    repeat(3) {
      limiter.tryAcquire("k", 3, window, start)
    }

    assertTrue(limiter.tryAcquire("k", 3, window, start.plus(window)))
  }

  @Test
  fun `keys are independent`() {
    repeat(3) {
      limiter.tryAcquire("a", 3, window, start)
    }

    assertTrue(limiter.tryAcquire("b", 3, window, start))
  }

  /**
   * Keys come from remote addresses, so without sweeping, a caller rotating source addresses turns
   * the limiter into the thing that exhausts memory.
   */
  @Test
  fun `sweeping drops stale keys so the map cannot grow without bound`() {
    repeat(3) {
      limiter.tryAcquire("k", 3, window, start)
    }
    assertFalse(limiter.tryAcquire("k", 3, window, start))

    limiter.sweep(window, start.plus(Duration.ofMinutes(5)))

    assertTrue(limiter.tryAcquire("k", 3, window, start.plus(Duration.ofMinutes(5))))
  }
}
