package net.bestia.login.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.bestia.login.jwt.JwtConfig
import net.bestia.login.jwt.JwtService
import net.bestia.login.metrics.LoginStateMeters.CachedCount
import net.bestia.login.ratelimit.RateLimiter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class LoginMetricsTest {

  private val registry = SimpleMeterRegistry()
  private val metrics = LoginMetrics(registry)

  @Test
  fun `every series exists at zero before anything happens`() {
    assertEquals(0.0, registry.get("login.refresh.reuse.detected").counter().count())
    assertEquals(0.0, registry.get("login.token.invalid").counter().count())
    assertEquals(0.0, registry.get("login.grant.refused").tag("reason", "pkce_mismatch").tag("client", "game").tag("flow", "exchange").counter().count())
    assertEquals(0.0, registry.get("login.kick.published").tag("reason", "BANNED").tag("result", "error").counter().count())
    assertEquals(0L, registry.get("login.sweep.duration").tag("job", "refresh_token").timer().count())
  }

  @Test
  fun `a denial is tagged with its bucket and never with the address`() {
    val limiter = RateLimiter(metrics)

    repeat(3) { limiter.tryAcquire("exchange:203.0.113.7", 1, WINDOW, NOW) }
    limiter.tryAcquire("mystery:203.0.113.8", 0, WINDOW, NOW)

    assertEquals(2.0, registry.get("login.ratelimit.denied").tag("bucket", "exchange").counter().count())
    assertEquals(1.0, registry.get("login.ratelimit.denied").tag("bucket", "other").counter().count())

    val tagValues = registry.get("login.ratelimit.denied").counters().flatMap { c -> c.id.tags.map { it.value } }
    assertTrue(tagValues.none { it.contains("203.0.113") }, "no address may reach a tag: $tagValues")
  }

  @Test
  fun `an allowed call is not counted as a denial`() {
    RateLimiter(metrics).tryAcquire("start:203.0.113.7", 5, WINDOW, NOW)

    assertEquals(0.0, registry.get("login.ratelimit.denied").tag("bucket", "start").counter().count())
  }

  @Test
  fun `the limiter reports its live windows and how many a sweep dropped`() {
    val limiter = RateLimiter(metrics)
    limiter.tryAcquire("start:a", 5, WINDOW, NOW)
    limiter.tryAcquire("start:b", 5, WINDOW, NOW.plus(Duration.ofMinutes(20)))

    assertEquals(2, limiter.bucketCount)
    assertEquals(1, limiter.sweep(Duration.ofMinutes(10), NOW.plus(Duration.ofMinutes(25))))
    assertEquals(1, limiter.bucketCount)
  }

  @Test
  fun `a sweep counts what it removed and how often it ran`() {
    metrics.sweep(LoginMetrics.SweepJob.LOGIN_SESSION) { 3 }
    metrics.sweep(LoginMetrics.SweepJob.LOGIN_SESSION) { 0 }

    assertEquals(3.0, registry.get("login.sweep.removed").tag("job", "login_session").counter().count())
    assertEquals(2L, registry.get("login.sweep.duration").tag("job", "login_session").timer().count())
    assertEquals(0.0, registry.get("login.sweep.failed").tag("job", "login_session").counter().count())
  }

  @Test
  fun `a sweep that throws is counted as failed, still timed, and rethrown`() {
    assertThrows(IllegalStateException::class.java) {
      metrics.sweep(LoginMetrics.SweepJob.RATE_LIMIT) { throw IllegalStateException("database is down") }
    }

    assertEquals(1.0, registry.get("login.sweep.failed").tag("job", "rate_limit").counter().count())
    assertEquals(1L, registry.get("login.sweep.duration").tag("job", "rate_limit").timer().count())
    assertEquals(0.0, registry.get("login.sweep.removed").tag("job", "rate_limit").counter().count())
  }

  @Test
  fun `tokens are counted by type and an invalid api token by itself`() {
    val jwt = JwtService(
      JwtConfig(secret = SECRET, loginTokenMinutes = 2, companionSecret = SECRET + "-companion"),
      metrics
    )

    jwt.createLoginToken(1, net.bestia.account.Role.USER)
    jwt.createApiToken(1)
    jwt.createApiToken(1)
    jwt.createCompanionToken(1)

    assertEquals(1.0, issued(registry, "zone"))
    assertEquals(2.0, issued(registry, "api"))
    assertEquals(1.0, issued(registry, "companion"))

    assertNotNull(jwt.validateApiToken(jwt.createApiToken(1)))
    assertNull(jwt.validateApiToken("not.a.token"))
    assertNull(jwt.validateApiToken(jwt.createLoginToken(1, net.bestia.account.Role.USER)), "a zone token is not an api token")

    assertEquals(2.0, registry.get("login.token.invalid").counter().count())
  }

  @Test
  fun `a cached count asks its source once per interval`() {
    var now = 0L
    var asked = 0
    val count = CachedCount(Duration.ofSeconds(30), { asked++; asked.toLong() }, { now })

    assertEquals(1.0, count.get())
    assertEquals(1.0, count.get())
    now += Duration.ofSeconds(29).toNanos()
    assertEquals(1.0, count.get())
    now += Duration.ofSeconds(1).toNanos()
    assertEquals(2.0, count.get())
    assertEquals(2, asked)
  }

  @Test
  fun `a failing source keeps the last good value and is not retried on every scrape`() {
    var now = 0L
    var asked = 0
    var failing = false
    val count = CachedCount(Duration.ofSeconds(30), {
      asked++
      if (failing) throw IllegalStateException("database is down")
      7L
    }, { now })

    assertEquals(7.0, count.get())

    failing = true
    now += Duration.ofSeconds(30).toNanos()
    assertEquals(7.0, count.get())
    assertEquals(7.0, count.get())
    assertEquals(2, asked, "the failed query was not repeated within the interval")
  }

  @Test
  fun `a count that has never loaded is NaN, which a scrape leaves out`() {
    val count = CachedCount(Duration.ofSeconds(30), { throw IllegalStateException("database is down") })

    assertTrue(count.get().isNaN())
  }

  private fun issued(registry: MeterRegistry, type: String): Double {
    return registry.get("login.token.issued").tag("type", type).counter().count()
  }

  private companion object {
    const val SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm-requirements"
    val WINDOW: Duration = Duration.ofMinutes(1)
    val NOW: Instant = Instant.parse("2026-10-23T12:00:00Z")
  }
}
