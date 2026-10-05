package net.bestia.zone.socket

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageRateLimiterTest {

  private var now = 0L
  private val limiter = MessageRateLimiter(burst = 3, perSecond = 1, nanoTime = { now })

  @Test
  fun `a burst passes and the next message is refused`() {
    repeat(3) { assertTrue(limiter.tryAcquire()) }

    assertFalse(limiter.tryAcquire())
  }

  @Test
  fun `the bucket refills at the configured rate`() {
    repeat(3) { limiter.tryAcquire() }

    now += 1_000_000_000L

    assertTrue(limiter.tryAcquire())
    assertFalse(limiter.tryAcquire())
  }
}
