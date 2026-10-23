package net.bestia.zone.account.authentication

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import net.bestia.account.Role
import net.bestia.bnet.proto.AuthenticationProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bus.contract.KickOrder
import net.bestia.zone.bus.BusProperties
import net.bestia.zone.config.ZoneConfig
import net.bestia.zone.message.ConnectionTerminator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals

/** What the zone counts about logins and kicks. */
class AuthMetricsTest {

  private val meters = SimpleMeterRegistry()
  private val metrics = AuthMetrics(meters)

  private val secret = "test-secret-key-that-is-at-least-32-characters-long-for-hmac-sha256"
  private val config = ZoneConfig(bestiaBaseSlotCount = 1, bestiaMaxSlotCount = 1, jwtAuthSecretKey = secret, shardId = 1)
  private val kickedAccounts = KickedAccounts(config)
  private val validator = LoginTokenValidator(config)
  private val processor = JwtAuthenticationProcessor(validator, kickedAccounts, metrics)

  private fun refused(reason: String): Double {
    return meters.get("zone.login.refused").tag("reason", reason).counter().count()
  }

  private fun envelope(token: String): EnvelopeProto.Envelope {
    return EnvelopeProto.Envelope.newBuilder()
      .setAuthentication(AuthenticationProto.Authentication.newBuilder().setToken(token))
      .build()
  }

  private fun token(issuer: String = "login", audience: String = "zone", subject: String = "1"): String {
    val key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

    return Jwts.builder()
      .subject(subject)
      .issuer(issuer)
      .audience().add(audience).and()
      .claim("role", Role.USER.name)
      .id(UUID.randomUUID().toString())
      .issuedAt(Date())
      .expiration(Date(System.currentTimeMillis() + 60_000))
      .signWith(key)
      .compact()
  }

  @Test
  fun `a good token is counted as accepted`() {
    processor.authenticate(envelope(token()))

    assertEquals(1.0, meters.get("zone.login.accepted").counter().count())
  }

  @Test
  fun `a token that is not signed for this zone is refused as invalid`() {
    processor.authenticate(envelope("not.a.token"))

    assertEquals(1.0, refused("invalid_token"))
  }

  @Test
  fun `the wrong issuer and the wrong audience are told apart`() {
    processor.authenticate(envelope(token(issuer = "somebody-else")))
    processor.authenticate(envelope(token(audience = "api")))

    assertEquals(1.0, refused("wrong_issuer"))
    assertEquals(1.0, refused("wrong_audience"))
  }

  @Test
  fun `a token presented twice is a reuse`() {
    val reused = token()

    processor.authenticate(envelope(reused))
    processor.authenticate(envelope(reused))

    assertEquals(1.0, meters.get("zone.login.accepted").counter().count())
    assertEquals(1.0, refused("token_reused"))
  }

  @Test
  fun `a token issued before a kick is refused as kicked`() {
    kickedAccounts.remember(1L, System.currentTimeMillis() + 10_000)

    processor.authenticate(envelope(token()))

    assertEquals(1.0, refused("kicked"))
    assertEquals(0.0, meters.get("zone.login.accepted").counter().count(), "the token was good, the account was not")
  }

  @Test
  fun `a handshake with no authentication in it is refused`() {
    processor.authenticate(EnvelopeProto.Envelope.getDefaultInstance())

    assertEquals(1.0, refused("no_payload"))
  }

  @Test
  fun `the reason a validator refused is on the exception`() {
    val exception = assertThrows<JwtLoginException> { validator.validateLoginToken(token(issuer = "x")) }

    assertEquals(AuthMetrics.Refusal.WRONG_ISSUER, exception.reason)
  }

  @Test
  fun `kicks are counted by what became of them`() {
    val connections = mockk<ConnectionTerminator> {
      every { disconnect(1L, any()) } returns true
      every { disconnect(2L, any()) } returns false
    }
    val listener = KickOrderListener(mockk(), BusProperties(nodeId = "test"), kickedAccounts, connections, metrics)
    val fresh = System.currentTimeMillis() + 1_000

    listener.handle(KickOrder(1L, "BANNED", fresh))
    listener.handle(KickOrder(2L, "GM_KICK", fresh))
    listener.handle(KickOrder(3L, "GM_KICK", System.currentTimeMillis() - 60_000))

    fun kicks(outcome: String) = meters.get("zone.kicks").tag("outcome", outcome).counter().count()
    assertEquals(3.0, kicks("received"))
    assertEquals(1.0, kicks("closed"))
    assertEquals(1.0, kicks("no_connection"))
    assertEquals(1.0, kicks("predates_node"))
  }
}
