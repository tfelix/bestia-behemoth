package net.bestia.login.metrics

import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.FunctionTimer
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import net.bestia.bus.contract.KickOrder
import net.bestia.bus.valkey.ValkeyStreamAppender
import net.bestia.login.account.AccountRepository
import net.bestia.login.gamelogin.AuthorizationCodeRepository
import net.bestia.login.gamelogin.LoginSessionRepository
import net.bestia.login.gamelogin.RefreshTokenRepository
import net.bestia.login.ratelimit.RateLimiter
import net.bestia.login.webauthn.WebAuthnCeremonyRepository
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * What the login server holds, read when Prometheus scrapes: row counts of the tables a login touches, the limiter's
 * live buckets and the bus appender's own tallies.
 *
 * Every count is a query, and a scrape comes every few seconds, so each is answered from a value at most [ttl] old
 * rather than asking MariaDB again.
 */
@Component
class LoginStateMeters(
  private val accounts: AccountRepository,
  private val loginSessions: LoginSessionRepository,
  private val refreshTokens: RefreshTokenRepository,
  private val authorizationCodes: AuthorizationCodeRepository,
  private val ceremonies: WebAuthnCeremonyRepository,
  private val rateLimiter: RateLimiter,
  private val kickOrders: ValkeyStreamAppender<KickOrder>,
  private val ttl: Duration = Duration.ofSeconds(DEFAULT_TTL_SECONDS),
) : MeterBinder {

  override fun bindTo(registry: MeterRegistry) {
    cachedGauge(registry, "login.accounts", "Accounts that exist") {
      accounts.count()
    }
    cachedGauge(registry, "login.sessions.live", "Login sessions that have not expired yet") {
      loginSessions.countByExpiresAtAfter(LocalDateTime.now())
    }
    cachedGauge(registry, "login.refresh.tokens.usable", "Refresh tokens that could still be spent") {
      refreshTokens.countByRevokedAtIsNullAndConsumedAtIsNullAndExpiresAtAfter(LocalDateTime.now())
    }
    cachedGauge(registry, "login.authcodes.outstanding", "Authorization codes issued and not yet exchanged") {
      authorizationCodes.countByConsumedAtIsNullAndExpiresAtAfter(LocalDateTime.now())
    }
    cachedGauge(registry, "login.ceremonies.open", "Passkey ceremonies started and not yet finished") {
      ceremonies.countByExpiresAtAfter(LocalDateTime.now())
    }

    Gauge.builder("login.ratelimit.buckets", rateLimiter) { it.bucketCount.toDouble() }
      .description("Rate-limit windows held in memory")
      .register(registry)

    FunctionCounter.builder("login.bus.append", kickOrders) { it.appendsSucceeded.toDouble() }
      .tag("result", "ok")
      .description("Kick orders Valkey took")
      .register(registry)
    FunctionCounter.builder("login.bus.append", kickOrders) { it.appendsFailed.toDouble() }
      .tag("result", "error")
      .register(registry)
    FunctionTimer.builder(
      "login.bus.append.duration",
      kickOrders,
      { (it.appendsSucceeded + it.appendsFailed) },
      { it.appendNanos.toDouble() },
      TimeUnit.NANOSECONDS,
    )
      .description("Time the bus appender waited on Valkey, failed appends included")
      .register(registry)
  }

  private fun cachedGauge(registry: MeterRegistry, name: String, description: String, load: () -> Long) {
    val cached = CachedCount(ttl, load)

    Gauge.builder(name, cached) { it.get() }.description(description).register(registry)
  }

  /**
   * A count that asks its source at most once per [ttl]. A source that fails is not asked again until the ttl has
   * passed either, so a database that is down costs one failed query per interval and not one per scrape; the
   * last good value is kept meanwhile, and NaN - which Prometheus leaves out - before there has been one.
   */
  class CachedCount(
    private val ttl: Duration,
    private val load: () -> Long,
    private val nanoTime: () -> Long = System::nanoTime,
  ) {

    private var value = Double.NaN
    private var loadedAt: Long? = null

    @Synchronized
    fun get(): Double {
      val now = nanoTime()
      val last = loadedAt

      if (last == null || now - last >= ttl.toNanos()) {
        loadedAt = now

        try {
          value = load().toDouble()
        } catch (e: Exception) {
          LOG.warn(e) { "Could not count rows for a login metric" }
        }
      }

      return value
    }
  }

  private companion object {
    const val DEFAULT_TTL_SECONDS = 30L

    private val LOG = KotlinLogging.logger { }
  }
}
