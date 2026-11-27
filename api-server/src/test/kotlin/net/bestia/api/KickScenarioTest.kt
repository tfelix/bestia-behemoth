package net.bestia.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import net.bestia.bus.BusKeys
import net.bestia.bus.contract.KickOrder
import net.bestia.bus.valkey.ValkeyStreamAppender
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** A kick from the login server, appended as the `login` user, locks the account out of the companion app. */
class KickScenarioTest : ApiScenarioTestBase() {

  @Autowired
  private lateinit var rest: TestRestTemplate

  @LocalServerPort
  private var port: Int = 0

  private val mapper = jacksonObjectMapper()
  private lateinit var loginClient: RedisClient
  private lateinit var kicks: ValkeyStreamAppender<KickOrder>

  @BeforeAll
  fun connectAsLoginServer() {
    val uri = RedisURI.Builder.redis(ApiTestContainers.valkey.host, ApiTestContainers.valkey.getMappedPort(6379))
      .withAuthentication("login", "login-dev-password".toCharArray())
      .build()
    loginClient = RedisClient.create(uri)
    kicks = ValkeyStreamAppender(loginClient, BusKeys.KICKS, { it.accountId })
  }

  @AfterAll
  fun disconnect() {
    kicks.close()
    loginClient.shutdown()
  }

  @Test
  fun `a token issued before the kick is refused, one issued after it is not`() {
    val kickedAt = Instant.now()
    val before = token(81, issuedAt = kickedAt.minusSeconds(10))
    val after = token(81, issuedAt = kickedAt.plusSeconds(2))

    kick(81, kickedAt)

    await().atMost(Duration.ofSeconds(10)).until { status(before) == 401 }
    assertNotEquals(401, status(after))
  }

  @Test
  fun `an open socket of a kicked account is closed as unauthorized`() {
    val closed = CompletableFuture<CloseStatus>()
    val ready = CompletableFuture<Unit>()
    val handler = object : TextWebSocketHandler() {
      override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        ready.complete(Unit)
      }

      override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        closed.complete(status)
      }
    }
    val socket = StandardWebSocketClient().execute(handler, "ws://localhost:$port/v1/companion/ws").get(5, TimeUnit.SECONDS)
    val hello = mapOf("type" to "auth", "token" to token(82, issuedAt = Instant.now().minusSeconds(10)))
    socket.sendMessage(TextMessage(mapper.writeValueAsString(hello)))
    ready.get(5, TimeUnit.SECONDS)

    kick(82, Instant.now())

    assertEquals(4000, closed.get(10, TimeUnit.SECONDS).code)
  }

  private fun kick(accountId: Long, at: Instant) {
    kicks.append(KickOrder(accountId, "BANNED", at.toEpochMilli()))
  }

  private fun status(token: String): Int {
    val headers = HttpHeaders().apply { setBearerAuth(token) }
    return rest.exchange("/v1/companion/masters", HttpMethod.GET, HttpEntity<Void>(headers), String::class.java).statusCode.value()
  }

  /** Minted as login-server mints it. */
  private fun token(accountId: Long, issuedAt: Instant): String {
    val key = Keys.hmacShaKeyFor("api-test-companion-secret-long-enough-for-hmac".toByteArray(StandardCharsets.UTF_8))
    return Jwts.builder()
      .subject(accountId.toString())
      .issuer("login")
      .audience().add("companion").and()
      .issuedAt(Date.from(issuedAt))
      .expiration(Date(System.currentTimeMillis() + 600_000))
      .signWith(key)
      .compact()
  }
}
