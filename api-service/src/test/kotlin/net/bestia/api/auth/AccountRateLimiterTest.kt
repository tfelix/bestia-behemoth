package net.bestia.api.auth

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.bestia.api.metrics.AuthMetrics
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountRateLimiterTest {

  private val registry = SimpleMeterRegistry()
  private val limiter = AccountRateLimiter(
    CompanionProperties(jwtSecret = "unused", requestsPerMinute = 2, mapRequestsPerMinute = 1),
    AuthMetrics(registry),
  )

  @Test
  fun `a call over the budget is refused and counted by which budget it was`() {
    assertTrue(limiter.tryAcquire(1, map = false))
    assertTrue(limiter.tryAcquire(1, map = false))
    assertFalse(limiter.tryAcquire(1, map = false))
    assertFalse(limiter.tryAcquire(1, map = false))
    assertTrue(limiter.tryAcquire(1, map = true))
    assertFalse(limiter.tryAcquire(1, map = true))

    assertEquals(2.0, denied(map = false))
    assertEquals(1.0, denied(map = true))
  }

  @Test
  fun `a new window forgives, and an untouched budget reads zero`() {
    assertEquals(0.0, denied(map = false))

    limiter.tryAcquire(1, map = true)
    limiter.newWindow()

    assertTrue(limiter.tryAcquire(1, map = true))
  }

  private fun denied(map: Boolean): Double {
    return registry.get("api.auth.ratelimit.denied").tag("map", map.toString()).counter().count()
  }
}
