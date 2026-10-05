package net.bestia.zone.internal

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.internal.ServiceTokens
import net.bestia.zone.ZoneConfig
import net.bestia.zone.mocks.GameClientMockFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The login server ends a banned or recovered account's game session through this endpoint. It shares the port
 * of the map tiles, so the token is all that keeps a player from kicking anyone else.
 *
 * The properties match `MapTileControllerTest`, so both share one Spring context.
 */
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = [
    "spring.main.web-application-type=servlet",
    "spring.datasource.url=jdbc:h2:mem:behemoth-maptile"
  ]
)
@ActiveProfiles("no-socket", "test")
class InternalKickEndpointTest {

  @Autowired
  private lateinit var rest: TestRestTemplate

  @Autowired
  private lateinit var zoneConfig: ZoneConfig

  @Autowired
  private lateinit var connections: GameClientMockFactory.MockConnectionAdapter

  @BeforeEach
  fun forgetEarlierKicks() {
    connections.disconnectReasons.clear()
  }

  @Test
  fun `a kick signed by the login server disconnects the account`() {
    val response = kick(ACCOUNT, serviceToken(ACCOUNT))

    assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    assertEquals("BANNED", connections.disconnectReasons[ACCOUNT])
  }

  @Test
  fun `a kick without a token is refused`() {
    val response = kick(ACCOUNT, token = null)

    assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    assertNull(connections.disconnectReasons[ACCOUNT])
  }

  @Test
  fun `a login token does not kick`() {
    val response = kick(ACCOUNT, serviceToken(ACCOUNT, audience = "zone"))

    assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    assertNull(connections.disconnectReasons[ACCOUNT])
  }

  @Test
  fun `a token for another account does not kick this one`() {
    val response = kick(ACCOUNT, serviceToken(OTHER_ACCOUNT))

    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertNull(connections.disconnectReasons[ACCOUNT])
  }

  @Test
  fun `a token with another scope does not kick`() {
    val response = kick(ACCOUNT, serviceToken(ACCOUNT, scope = "teleport"))

    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertNull(connections.disconnectReasons[ACCOUNT])
  }

  @Test
  fun `a token is good for one call`() {
    val token = serviceToken(ACCOUNT)
    kick(ACCOUNT, token)

    assertEquals(HttpStatus.UNAUTHORIZED, kick(ACCOUNT, token).statusCode)
  }

  @Test
  fun `a token that lives longer than a service token is refused`() {
    val response = kick(ACCOUNT, serviceToken(ACCOUNT, lifetimeSeconds = 3600))

    assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    assertNull(connections.disconnectReasons[ACCOUNT])
  }

  private fun kick(accountId: Long, token: String?): org.springframework.http.ResponseEntity<String> {
    val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
    token?.let { headers.setBearerAuth(it) }

    return rest.postForEntity(
      ServiceTokens.kickPath(accountId),
      HttpEntity(mapOf("reason" to "BANNED"), headers),
      String::class.java
    )
  }

  private fun serviceToken(
    accountId: Long,
    audience: String = ServiceTokens.AUDIENCE,
    scope: String = ServiceTokens.KICK_SCOPE,
    lifetimeSeconds: Long = ServiceTokens.LIFETIME_SECONDS
  ): String {
    val key = Keys.hmacShaKeyFor(zoneConfig.jwtAuthSecretKey.toByteArray(StandardCharsets.UTF_8))
    val now = System.currentTimeMillis()

    return Jwts.builder()
      .issuer(ServiceTokens.ISSUER)
      .audience().add(audience).and()
      .id(UUID.randomUUID().toString())
      .claim(ServiceTokens.ACCOUNT_CLAIM, accountId)
      .claim(ServiceTokens.SCOPE_CLAIM, scope)
      .issuedAt(Date(now))
      .expiration(Date(now + lifetimeSeconds * 1000))
      .signWith(key)
      .compact()
  }

  private companion object {
    const val ACCOUNT = 1L
    const val OTHER_ACCOUNT = 2L
  }
}
