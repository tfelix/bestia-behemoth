package net.bestia.login.zone

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.account.KickReason
import net.bestia.internal.ServiceTokens
import net.bestia.login.jwt.JwtConfig
import net.bestia.login.jwt.JwtService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

/** The login server cannot tell which zone a player is on, so a kick goes to every zone that might hold them. */
class HttpZoneKickerTest {

  private val first = ZoneStub()
  private val second = ZoneStub()
  private val jwtConfig = JwtConfig(secret = "login-test-secret-that-is-long-enough-for-hmac", loginTokenMinutes = 2)

  @AfterEach
  fun stopZones() {
    first.close()
    second.close()
  }

  @Test
  fun `every candidate zone is told to kick the account`() {
    kicker(first.endpoint, second.endpoint).kick(ACCOUNT, KickReason.BANNED)

    listOf(first, second).forEach { zone ->
      val call = zone.calls.single()
      assertEquals("POST", call.method)
      assertEquals(ServiceTokens.kickPath(ACCOUNT), call.path)
      assertTrue(call.body.contains("BANNED"), call.body)
    }
  }

  @Test
  fun `the kick carries a service token for that account`() {
    kicker(first.endpoint).kick(ACCOUNT, KickReason.BANNED)

    val claims = Jwts.parser()
      .verifyWith(Keys.hmacShaKeyFor(jwtConfig.secret.toByteArray(StandardCharsets.UTF_8)))
      .build()
      .parseSignedClaims(first.calls.single().authorization!!.removePrefix("Bearer "))
      .payload

    assertEquals(ServiceTokens.ISSUER, claims.issuer)
    assertEquals(setOf(ServiceTokens.AUDIENCE), claims.audience)
    assertEquals(ACCOUNT, (claims[ServiceTokens.ACCOUNT_CLAIM] as Number).toLong())
    assertEquals(ServiceTokens.KICK_SCOPE, claims[ServiceTokens.SCOPE_CLAIM])
  }

  @Test
  fun `a zone that cannot be reached does not stop the others`() {
    val gone = ZoneStub().also { it.close() }

    val kickedOn = kicker(gone.endpoint, first.endpoint).kick(ACCOUNT, KickReason.BANNED)

    assertEquals(listOf(first.endpoint.name), kickedOn)
    assertEquals(1, first.calls.size)
  }

  private fun kicker(vararg zones: ZoneEndpoint): HttpZoneKicker {
    val config = ZoneDirectoryConfig(zones = zones.toList(), requestTimeoutMillis = 2000)

    return HttpZoneKicker(ConfiguredZoneDirectory(config), JwtService(jwtConfig), config)
  }

  private companion object {
    const val ACCOUNT = 7L
  }
}
