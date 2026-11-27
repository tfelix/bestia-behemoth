package net.bestia.api

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import net.bestia.bus.contract.LoginCommand
import net.bestia.bus.contract.LoginReply.Outcome
import net.bestia.bus.valkey.ValkeyLoginCommandServer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals

/** The companion app's GM actions, against a stand-in login server on the real bus as the `login` user. */
class GmScenarioTest : ApiScenarioTestBase() {

  @Autowired
  private lateinit var rest: TestRestTemplate

  private lateinit var loginClient: RedisClient
  private var login: ValkeyLoginCommandServer? = null

  @Volatile
  private var outcome = Outcome.DONE
  private val received = CopyOnWriteArrayList<Pair<Long, LoginCommand>>()

  @BeforeAll
  fun connectAsLoginServer() {
    val uri = RedisURI.Builder.redis(ApiTestContainers.valkey.host, ApiTestContainers.valkey.getMappedPort(6379))
      .withAuthentication("login", "login-dev-password".toCharArray())
      .build()
    loginClient = RedisClient.create(uri)
    startLogin()
  }

  @AfterAll
  fun disconnect() {
    login?.close()
    loginClient.shutdown()
  }

  @BeforeEach
  fun reset() {
    received.clear()
    outcome = Outcome.DONE
  }

  @Test
  fun `a ban names the calling account as the GM`() {
    val response = post("/v1/companion/gm/accounts/9/ban", mapOf("note" to "botting", "durationHours" to 24), actor = 3)

    assertEquals(204, response.statusCode.value())
    assertEquals(listOf<Pair<Long, LoginCommand>>(3L to LoginCommand.Ban(9, "botting", 24)), received.toList())
  }

  @Test
  fun `a kick without a duration reaches the login server`() {
    assertEquals(204, post("/v1/companion/gm/accounts/9/kick", mapOf("note" to "stuck"), actor = 3).statusCode.value())

    assertEquals(listOf<Pair<Long, LoginCommand>>(3L to LoginCommand.Kick(9, "stuck")), received.toList())
  }

  @Test
  fun `the login server's refusals become HTTP statuses`() {
    val statuses = mapOf(
      Outcome.INVALID to 400,
      Outcome.NOT_ALLOWED to 403,
      Outcome.NO_SUCH_ACCOUNT to 404,
      Outcome.FAILED to 502,
    )

    for ((refusal, status) in statuses) {
      outcome = refusal
      assertEquals(status, post("/v1/companion/gm/accounts/9/kick", mapOf("note" to "x"), actor = 3).statusCode.value())
    }
  }

  @Test
  fun `a call without a companion token never reaches the login server`() {
    val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
    val response = rest.postForEntity("/v1/companion/gm/accounts/9/ban", HttpEntity(mapOf("note" to "x"), headers), String::class.java)

    assertEquals(401, response.statusCode.value())
    assertEquals(emptyList(), received.toList())
  }

  @Test
  fun `with no login server listening the action is a 503`() {
    login?.close()
    try {
      assertEquals(503, post("/v1/companion/gm/accounts/9/kick", mapOf("note" to "x"), actor = 3).statusCode.value())
    } finally {
      startLogin()
    }
  }

  private fun startLogin() {
    val server = ValkeyLoginCommandServer(loginClient, { actor, command ->
      received += actor to command
      outcome
    })
    login = server
    await().atMost(Duration.ofSeconds(10)).until { server.isListening }
  }

  private fun post(path: String, body: Any, actor: Long): ResponseEntity<String> {
    val headers = HttpHeaders().apply {
      setBearerAuth(token(actor))
      contentType = MediaType.APPLICATION_JSON
    }
    return rest.postForEntity(path, HttpEntity(body, headers), String::class.java)
  }

  /** Minted as login-server mints it. */
  private fun token(accountId: Long): String {
    val key = Keys.hmacShaKeyFor("api-test-companion-secret-long-enough-for-hmac".toByteArray(StandardCharsets.UTF_8))
    return Jwts.builder()
      .subject(accountId.toString())
      .issuer("login")
      .audience().add("companion").and()
      .expiration(Date(System.currentTimeMillis() + 600_000))
      .signWith(key)
      .compact()
  }
}
