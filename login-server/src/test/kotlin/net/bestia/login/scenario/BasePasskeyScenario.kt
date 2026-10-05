package net.bestia.login.scenario

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import net.bestia.login.jwt.JwtConfig
import net.bestia.login.ratelimit.RateLimiter
import net.bestia.login.util.SecureTokens
import net.bestia.login.webauthn.VirtualAuthenticator
import net.bestia.login.webauthn.WebAuthnConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Drives the browser-mediated login the way the game and the login page really do, with a virtual
 * authenticator standing in for the operating system passkey provider.
 *
 * Everything a scenario needs to *reach* a signed-in state lives here; what each scenario asserts
 * about it lives in the scenario. The mechanics are exercised for real - the loopback redirect URI,
 * PKCE, the one-time code - so a token that comes out of [exchange] is one zone-server would accept.
 */
abstract class BasePasskeyScenario : BaseLoginScenario() {

  @Autowired
  protected lateinit var restTemplate: TestRestTemplate

  @Autowired
  protected lateinit var webAuthnConfig: WebAuthnConfig

  @Autowired
  private lateinit var jwtConfig: JwtConfig

  @Autowired
  private lateinit var rateLimiter: RateLimiter

  protected val mapper = ObjectMapper()

  @BeforeEach
  fun resetLimits() {
    // Every test in a class shares one loopback address, so without this the later ones start
    // failing on the limiter rather than on whatever they mean to assert.
    rateLimiter.reset()
  }

  // --- flow helpers -------------------------------------------------------------------------

  protected fun register(
    authenticator: VirtualAuthenticator,
    displayName: String = uniqueDisplayName()
  ): Completed {
    val session = start()

    val options = post(
      "/api/v1/webauthn/register/options",
      mapOf("session_id" to session.sessionId, "display_name" to displayName),
      session.cookie
    )

    val publicKey = options.get("public_key")
    val userHandle = decodeB64u(publicKey.get("user").get("id").asText())

    val verified = createCredential(authenticator, options, "/api/v1/webauthn/register/verify", session.cookie)

    return Completed(
      code = completeAndTakeCode(session),
      verifier = session.verifier,
      userHandle = userHandle,
      recoveryCodes = verified.get("recovery_codes").map { it.asText() }
    )
  }

  protected fun recover(
    authenticator: VirtualAuthenticator,
    displayName: String,
    recoveryCode: String
  ): Completed {
    val session = start()

    val options = post(
      "/api/v1/webauthn/recover/options",
      mapOf(
        "session_id" to session.sessionId,
        "display_name" to displayName,
        "recovery_code" to recoveryCode
      ),
      session.cookie
    )

    val userHandle = decodeB64u(options.get("public_key").get("user").get("id").asText())
    val verified = createCredential(authenticator, options, "/api/v1/webauthn/register/verify", session.cookie)

    return Completed(
      code = completeAndTakeCode(session),
      verifier = session.verifier,
      userHandle = userHandle,
      recoveryCodes = verified.get("recovery_codes").map { it.asText() }
    )
  }

  protected fun signIn(authenticator: VirtualAuthenticator, userHandle: ByteArray): Completed {
    val session = start()
    assertOn(session, authenticator, userHandle)

    return Completed(
      code = completeAndTakeCode(session),
      verifier = session.verifier,
      userHandle = userHandle,
      recoveryCodes = emptyList()
    )
  }

  protected fun assertOn(
    session: StartedSession,
    authenticator: VirtualAuthenticator,
    userHandle: ByteArray
  ) {
    val options = post("/api/v1/webauthn/assert/options", mapOf("session_id" to session.sessionId), session.cookie)
    val publicKey = options.get("public_key")

    // Usernameless: nothing identifying the account was sent, so the credential has to be
    // discoverable and the account is resolved from the handle the authenticator returns.
    assertTrue(publicKey.get("allowCredentials") == null || publicKey.get("allowCredentials").isEmpty)

    val credential = authenticator.get(
      webAuthnConfig.rpId,
      publicKey.get("challenge").asText(),
      origin(),
      userHandle
    )

    post(
      "/api/v1/webauthn/assert/verify",
      mapOf("ceremony_id" to options.get("ceremony_id").asText(), "credential" to mapper.readTree(credential)),
      session.cookie
    )
  }

  protected fun addCredential(session: StartedSession, authenticator: VirtualAuthenticator) {
    val options = post("/api/v1/webauthn/credentials/options", mapOf("session_id" to session.sessionId), session.cookie)

    createCredential(authenticator, options, "/api/v1/webauthn/credentials/verify", session.cookie)
  }

  protected fun createCredential(
    authenticator: VirtualAuthenticator,
    options: JsonNode,
    verifyPath: String,
    cookie: String?
  ): JsonNode {
    val credential = authenticator.create(
      webAuthnConfig.rpId,
      options.get("public_key").get("challenge").asText(),
      origin()
    )

    return post(
      verifyPath,
      mapOf("ceremony_id" to options.get("ceremony_id").asText(), "credential" to mapper.readTree(credential)),
      cookie
    )
  }

  protected fun start(): StartedSession {
    val verifier = SecureTokens.randomToken()
    val state = SecureTokens.randomToken()

    val response = post(
      "/api/v1/auth/game/start",
      startBody("http://127.0.0.1:$LOOPBACK_PORT/callback", state, verifier)
    )

    val loginUrl = response.get("login_url").asText()
    assertTrue(loginUrl.contains("/game-login"))

    // The game opens the link in the browser; that first page load is what the browser half is bound to.
    val loginPath = UriComponentsBuilder.fromUriString(loginUrl).build().let { "${it.path}?${it.query}" }

    return StartedSession(
      sessionId = response.get("session_id").asText(),
      verifier = verifier,
      state = state,
      loginPath = loginPath,
      cookie = bindingCookieOf(openLoginPage(loginPath))
    )
  }

  protected fun openLoginPage(loginPath: String, cookie: String? = null): ResponseEntity<String> {
    val headers = HttpHeaders()
    cookie?.let { headers.add(HttpHeaders.COOKIE, it) }

    return restTemplate.exchange(URI.create(loginPath), HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)
  }

  /** The cookie the login page set, as a `name=value` pair ready to send back. */
  protected fun bindingCookieOf(page: ResponseEntity<String>): String? {
    return page.headers[HttpHeaders.SET_COOKIE]
      ?.firstOrNull { it.startsWith("$BINDING_COOKIE=") }
      ?.substringBefore(';')
  }

  protected fun startBody(
    redirectUri: String,
    state: String,
    verifier: String = SecureTokens.randomToken()
  ): Map<String, String> {
    return mapOf(
      "redirect_uri" to redirectUri,
      "code_challenge" to SecureTokens.base64Url(SecureTokens.sha256(verifier)),
      "code_challenge_method" to "S256",
      "state" to state,
      "intent" to "LOGIN"
    )
  }

  /**
   * The browser last step. The code is only minted here, which is why the sixty second lifetime is
   * not spent while the player reads their recovery codes.
   */
  protected fun completeAndTakeCode(session: StartedSession): String {
    val completed = post("/api/v1/auth/session/complete", mapOf("session_id" to session.sessionId), session.cookie)
    val query = UriComponentsBuilder.fromUriString(completed.get("redirect_to").asText()).build().queryParams

    assertEquals(session.state, query.getFirst("state"))

    return query.getFirst("code")!!
  }

  protected fun exchange(code: String, verifier: String): String {
    return exchangeFully(code, verifier).token
  }

  /** Both halves of a successful exchange, for the tests that care about the standing session too. */
  protected fun exchangeFully(code: String, verifier: String): Tokens {
    val response = rawExchange(code, verifier)

    assertEquals(200, response.statusCode.value(), "exchange returned ${response.body}")

    return tokensOf(response)
  }

  protected fun rawExchange(code: String, verifier: String): ResponseEntity<String> {
    return rawPost("/api/v1/auth/game/exchange", mapOf("code" to code, "code_verifier" to verifier))
  }

  /** A resume: no browser, no loopback listener, no authenticator. */
  protected fun refresh(refreshToken: String): Tokens {
    val response = rawRefresh(refreshToken)

    assertEquals(200, response.statusCode.value(), "refresh returned ${response.body}")

    return tokensOf(response)
  }

  protected fun rawRefresh(refreshToken: String): ResponseEntity<String> {
    return rawPost("/api/v1/auth/game/refresh", mapOf("refresh_token" to refreshToken))
  }

  protected fun rawRevoke(refreshToken: String): ResponseEntity<String> {
    return rawPost("/api/v1/auth/game/revoke", mapOf("refresh_token" to refreshToken))
  }

  private fun tokensOf(response: ResponseEntity<String>): Tokens {
    val body = mapper.readTree(response.body)

    return Tokens(
      token = body.get("token").asText(),
      refreshToken = body.get("refresh_token").asText()
    )
  }

  /**
   * Verified the way zone-server verifies it - same secret, same issuer and audience checks - so a
   * token that passes here is one the game socket would actually accept.
   */
  protected fun accountIdOf(token: String): Long {
    val key = Keys.hmacShaKeyFor(jwtConfig.secret.toByteArray(StandardCharsets.UTF_8))
    val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload

    assertEquals("login", claims.issuer)
    assertTrue(claims.audience.contains("zone"))
    assertEquals("USER", claims.get("role", String::class.java))

    return claims.subject.toLong()
  }

  // --- transport ----------------------------------------------------------------------------

  protected fun post(path: String, body: Any, cookie: String? = null): JsonNode {
    val response = rawPost(path, body, cookie)

    assertEquals(200, response.statusCode.value(), "POST $path returned ${response.body}")

    return mapper.readTree(response.body)
  }

  protected fun rawPost(path: String, body: Any, cookie: String? = null): ResponseEntity<String> {
    val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
    cookie?.let { headers.add(HttpHeaders.COOKIE, it) }

    return restTemplate.postForEntity(path, HttpEntity(body, headers), String::class.java)
  }

  protected fun origin(): String {
    return webAuthnConfig.origins.first()
  }

  protected fun uniqueDisplayName(): String {
    return "Player" + SecureTokens.randomToken(12).filter { it.isLetterOrDigit() }.take(20)
  }

  protected fun decodeB64u(value: String): ByteArray {
    return Base64.getUrlDecoder().decode(value)
  }

  protected data class StartedSession(
    val sessionId: String,
    val verifier: String,
    val state: String,
    val loginPath: String,
    /** Null until the server binds the browser half; every browser-side call sends it back. */
    val cookie: String?
  )

  protected data class Completed(
    val code: String,
    val verifier: String,
    val userHandle: ByteArray,
    val recoveryCodes: List<String>
  )

  /** What the game comes away with: one handoff ticket for the zone, one standing session. */
  protected data class Tokens(
    val token: String,
    val refreshToken: String
  )

  companion object {
    /** Never bound in the test: the server only records the redirect target, it never calls it. */
    const val LOOPBACK_PORT = 49721
    const val RECOVERY_CODE_COUNT = 10
    const val BINDING_COOKIE = "bestia_login_binding"
  }
}
