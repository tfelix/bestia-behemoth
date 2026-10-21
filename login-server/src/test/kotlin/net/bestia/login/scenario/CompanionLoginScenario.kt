package net.bestia.login.scenario

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.login.jwt.JwtConfig
import net.bestia.login.webauthn.VirtualAuthenticator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import java.nio.charset.StandardCharsets
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * The companion app's login: the game's ceremony, redirected to an allowlisted page, ending in a companion token
 * and a refresh cookie. Nothing a companion login hands out may be turned into a zone token, and nothing the game
 * holds may be turned into a companion session.
 */
class CompanionLoginScenario : BasePasskeyScenario() {

  @Autowired
  private lateinit var jwt: JwtConfig

  @Test
  fun `a companion login ends in a companion token and an httpOnly refresh cookie`() {
    val completed = register(VirtualAuthenticator(), session = startCompanion())

    val response = companionExchange(completed.code, completed.verifier)

    assertEquals(200, response.statusCode.value(), "exchange returned ${response.body}")
    val body = mapper.readTree(response.body)
    assertEquals(body.get("account_id").asLong(), companionAccountOf(body.get("access_token").asText()))

    val cookie = refreshCookieOf(response)
    assertTrue(cookie.contains("HttpOnly") && cookie.contains("SameSite=Strict")) { cookie }
    assertTrue(cookie.contains("Path=/api/v1/auth/companion")) { cookie }
  }

  @Test
  fun `the refresh cookie rotates and keeps yielding companion tokens`() {
    val completed = register(VirtualAuthenticator(), session = startCompanion())
    val first = refreshCookieOf(companionExchange(completed.code, completed.verifier)).substringBefore(';')

    val refreshed = rawPost("/api/v1/auth/companion/refresh", emptyMap<String, String>(), first)

    assertEquals(200, refreshed.statusCode.value(), "refresh returned ${refreshed.body}")
    assertNotEquals(first, refreshCookieOf(refreshed).substringBefore(';'))
    assertEquals(401, rawPost("/api/v1/auth/companion/refresh", emptyMap<String, String>(), first).statusCode.value())
  }

  @Test
  fun `a companion code is refused by the game's exchange`() {
    val completed = register(VirtualAuthenticator(), session = startCompanion())

    assertEquals(400, rawExchange(completed.code, completed.verifier).statusCode.value())
  }

  @Test
  fun `a game code is refused by the companion exchange`() {
    val completed = register(VirtualAuthenticator())

    assertEquals(401, companionExchange(completed.code, completed.verifier).statusCode.value())
  }

  @Test
  fun `a companion refresh token cannot resume the game, and survives being tried`() {
    val completed = register(VirtualAuthenticator(), session = startCompanion())
    val cookie = refreshCookieOf(companionExchange(completed.code, completed.verifier)).substringBefore(';')
    val raw = cookie.substringAfter('=')

    assertEquals(400, rawRefresh(raw).statusCode.value())
    assertEquals(200, rawPost("/api/v1/auth/companion/refresh", emptyMap<String, String>(), cookie).statusCode.value())
  }

  @Test
  fun `a redirect off the allowlist is refused`() {
    val response = rawPost(
      "/api/v1/auth/companion/start",
      startBody("http://localhost:5173/somewhere-else", "state-that-is-long-enough"),
    )

    assertEquals(400, response.statusCode.value())
  }

  @Test
  fun `signing out ends the refresh cookie`() {
    val completed = register(VirtualAuthenticator(), session = startCompanion())
    val cookie = refreshCookieOf(companionExchange(completed.code, completed.verifier)).substringBefore(';')

    val logout = rawPost("/api/v1/auth/companion/logout", emptyMap<String, String>(), cookie)

    assertEquals(204, logout.statusCode.value())
    assertTrue(refreshCookieOf(logout).contains("Max-Age=0"))
    assertEquals(401, rawPost("/api/v1/auth/companion/refresh", emptyMap<String, String>(), cookie).statusCode.value())
  }

  private fun startCompanion(): StartedSession {
    return start(path = "/api/v1/auth/companion/start", redirectUri = COMPANION_CALLBACK)
  }

  private fun companionExchange(code: String, verifier: String): ResponseEntity<String> {
    return rawPost("/api/v1/auth/companion/exchange", mapOf("code" to code, "code_verifier" to verifier))
  }

  private fun refreshCookieOf(response: ResponseEntity<String>): String {
    return response.headers[HttpHeaders.SET_COOKIE]?.firstOrNull { it.startsWith("bestia_companion_refresh=") }
      ?: fail("no refresh cookie in ${response.headers}")
  }

  /** Verified as the api-service verifies it: the companion key, and exactly the companion audience. */
  private fun companionAccountOf(token: String): Long {
    val key = Keys.hmacShaKeyFor(jwt.companionSecret.toByteArray(StandardCharsets.UTF_8))
    val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload

    assertEquals("login", claims.issuer)
    assertEquals(setOf("companion"), claims.audience)
    return claims.subject.toLong()
  }

  private companion object {
    const val COMPANION_CALLBACK = "http://localhost:5173/auth/callback"
  }
}
