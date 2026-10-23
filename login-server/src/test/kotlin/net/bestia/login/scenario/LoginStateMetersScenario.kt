package net.bestia.login.scenario

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.bestia.bus.contract.KickOrder
import net.bestia.bus.valkey.ValkeyStreamAppender
import net.bestia.login.account.AccountRepository
import net.bestia.login.gamelogin.AuthorizationCodeRepository
import net.bestia.login.gamelogin.LoginSessionRepository
import net.bestia.login.gamelogin.RefreshTokenRepository
import net.bestia.login.metrics.LoginStateMeters
import net.bestia.login.ratelimit.RateLimiter
import net.bestia.login.webauthn.VirtualAuthenticator
import net.bestia.login.webauthn.WebAuthnCeremonyRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.LocalDateTime

/** The gauges that read the database, against the database. The shared registry's copies are cached for 30 s, so these bind their own. */
class LoginStateMetersScenario : BasePasskeyScenario() {

  @Autowired
  private lateinit var accounts: AccountRepository

  @Autowired
  private lateinit var loginSessions: LoginSessionRepository

  @Autowired
  private lateinit var refreshTokens: RefreshTokenRepository

  @Autowired
  private lateinit var authorizationCodes: AuthorizationCodeRepository

  @Autowired
  private lateinit var ceremonies: WebAuthnCeremonyRepository

  @Autowired
  private lateinit var rateLimiter: RateLimiter

  @Autowired
  private lateinit var kickOrders: ValkeyStreamAppender<KickOrder>

  @Test
  fun `the gauges read what the tables hold`() {
    val registration = register(VirtualAuthenticator())
    exchangeFully(registration.code, registration.verifier)
    // A second login that is left half done: a session, a ceremony and no code yet.
    start()

    val registry = SimpleMeterRegistry()
    LoginStateMeters(
      accounts, loginSessions, refreshTokens, authorizationCodes, ceremonies, rateLimiter, kickOrders, Duration.ZERO
    ).bindTo(registry)
    val now = LocalDateTime.now()

    assertEquals(accounts.count().toDouble(), gauge(registry, "login.accounts"))
    assertTrue(gauge(registry, "login.accounts") >= 1.0)
    assertEquals(loginSessions.countByExpiresAtAfter(now).toDouble(), gauge(registry, "login.sessions.live"))
    assertEquals(
      refreshTokens.countByRevokedAtIsNullAndConsumedAtIsNullAndExpiresAtAfter(now).toDouble(),
      gauge(registry, "login.refresh.tokens.usable")
    )
    assertEquals(
      authorizationCodes.countByConsumedAtIsNullAndExpiresAtAfter(now).toDouble(),
      gauge(registry, "login.authcodes.outstanding")
    )
    assertEquals(ceremonies.countByExpiresAtAfter(now).toDouble(), gauge(registry, "login.ceremonies.open"))
    assertEquals(rateLimiter.bucketCount.toDouble(), gauge(registry, "login.ratelimit.buckets"))
  }

  private fun gauge(registry: SimpleMeterRegistry, name: String): Double {
    return registry.get(name).gauge().value()
  }
}
