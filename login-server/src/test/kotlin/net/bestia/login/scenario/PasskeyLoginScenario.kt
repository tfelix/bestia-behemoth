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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.util.UriComponentsBuilder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * The whole browser-mediated login, end to end, with a virtual authenticator standing in for the
 * operating system's passkey provider.
 *
 * Everything the game does is exercised for real - the loopback redirect URI, PKCE, the one-time
 * code - and the token that comes out is checked the way zone-server checks it, rather than merely
 * asserted non-null.
 */
class PasskeyLoginScenario : BaseLoginScenario() {

  @Autowired
  private lateinit var restTemplate: TestRestTemplate

  @Autowired
  private lateinit var webAuthnConfig: WebAuthnConfig

  @Autowired
  private lateinit var jwtConfig: JwtConfig

  @Autowired
  private lateinit var rateLimiter: RateLimiter

  private val mapper = ObjectMapper()

  @BeforeEach
  fun resetLimits() {
    // Every test in this class shares one loopback address, so without this the later ones start
    // failing on the limiter rather than on whatever they mean to assert.
    rateLimiter.reset()
  }

  @Test
  fun `a new account can be created and then signed in with the same passkey`() {
    val authenticator = VirtualAuthenticator()

    val registration = register(authenticator)
    val accountId = accountIdOf(exchange(registration.code, registration.verifier))

    // The point of the whole exercise: a later login, on another machine, resolves to the same
    // account purely from the user handle the authenticator returns.
    val login = signIn(authenticator, registration.userHandle)
    val loginToken = exchange(login.code, login.verifier)

    assertEquals(accountId, accountIdOf(loginToken))
  }

  /**
   * The multi-credential requirement. Two independent authenticators - think a laptop passkey and a
   * YubiKey - both reach one account, because a credential is not a device and the account is found
   * from the shared user handle.
   */
  @Test
  fun `a second credential enrolled on the account also signs in to it`() {
    val first = VirtualAuthenticator()
    val second = VirtualAuthenticator(backupEligible = false, backupState = false, incrementCounter = true)

    val registration = register(first)
    val accountId = accountIdOf(exchange(registration.code, registration.verifier))

    // Enrolled inside the session that just authenticated, which is what authorizes it.
    val session = start()
    assertOn(session, first, registration.userHandle)
    addCredential(session, second)

    val login = signIn(second, registration.userHandle)

    assertEquals(accountId, accountIdOf(exchange(login.code, login.verifier)))
  }

  @Test
  fun `registration hands out a full set of distinct recovery codes`() {
    val registration = register(VirtualAuthenticator())

    assertEquals(RECOVERY_CODE_COUNT, registration.recoveryCodes.size)
    assertEquals(RECOVERY_CODE_COUNT, registration.recoveryCodes.toSet().size)
  }

  /**
   * The whole reason the codes exist. The original authenticator is thrown away and a completely
   * new one takes over the account.
   */
  @Test
  fun `a recovery code enrols a replacement passkey and signs the player back in`() {
    val lost = VirtualAuthenticator()
    val replacement = VirtualAuthenticator()

    val displayName = uniqueDisplayName()
    val registration = register(lost, displayName)
    val accountId = accountIdOf(exchange(registration.code, registration.verifier))

    val recovered = recover(replacement, displayName, registration.recoveryCodes.first())

    assertEquals(accountId, accountIdOf(exchange(recovered.code, recovered.verifier)))

    // Recovery replaces the whole set, so nothing from the old one is still usable.
    val session = start()
    val response = rawPost(
      "/api/v1/webauthn/recover/options",
      mapOf(
        "session_id" to session.sessionId,
        "display_name" to displayName,
        "recovery_code" to registration.recoveryCodes[1]
      )
    )

    assertEquals(400, response.statusCode.value())
  }

  @Test
  fun `a recovery code cannot be redeemed twice`() {
    val displayName = uniqueDisplayName()
    val registration = register(VirtualAuthenticator(), displayName)
    val code = registration.recoveryCodes.first()

    recover(VirtualAuthenticator(), displayName, code)

    val session = start()
    val response = rawPost(
      "/api/v1/webauthn/recover/options",
      mapOf("session_id" to session.sessionId, "display_name" to displayName, "recovery_code" to code)
    )

    assertEquals(400, response.statusCode.value())
  }

  @Test
  fun `an authorization code cannot be exchanged twice`() {
    val registration = register(VirtualAuthenticator())

    assertEquals(200, rawExchange(registration.code, registration.verifier).statusCode.value())
    assertEquals(400, rawExchange(registration.code, registration.verifier).statusCode.value())
  }

  @Test
  fun `an authorization code is useless without the verifier that started the session`() {
    val registration = register(VirtualAuthenticator())

    assertEquals(400, rawExchange(registration.code, SecureTokens.randomToken()).statusCode.value())
  }

  @Test
  fun `a ceremony cannot be replayed`() {
    val authenticator = VirtualAuthenticator()
    val registration = register(authenticator)
    val session = start()

    val options = post("/api/v1/webauthn/assert/options", mapOf("session_id" to session.sessionId))
    val credential = authenticator.get(
      webAuthnConfig.rpId,
      options.get("public_key").get("challenge").asText(),
      origin(),
      registration.userHandle
    )
    val body = mapOf(
      "ceremony_id" to options.get("ceremony_id").asText(),
      "credential" to mapper.readTree(credential)
    )

    assertEquals(200, rawPost("/api/v1/webauthn/assert/verify", body).statusCode.value())

    // The challenge is destroyed when it is spent, so the identical response is worthless a second
    // time even though the signature over it is still perfectly valid.
    assertEquals(400, rawPost("/api/v1/webauthn/assert/verify", body).statusCode.value())
  }

  @Test
  fun `an assertion signed for a different relying party is refused`() {
    assertAssertionRefused(rpId = "attacker.example", origin = origin())
  }

  @Test
  fun `an assertion made on a foreign origin is refused`() {
    assertAssertionRefused(rpId = null, origin = "https://phishing.example")
  }

  /**
   * A synced passkey reports zero forever, because copies of it cannot agree on a counter. Treating
   * that as evidence of cloning would lock out everyone using iCloud Keychain or Google Password
   * Manager.
   */
  @Test
  fun `a synced passkey whose counter never moves keeps working`() {
    val authenticator = VirtualAuthenticator(incrementCounter = false)
    val registration = register(authenticator)

    repeat(3) {
      val login = signIn(authenticator, registration.userHandle)
      assertNotNull(exchange(login.code, login.verifier))
    }
  }

  /** The other half of the same rule: a hardware key that does move its counter still verifies. */
  @Test
  fun `a device bound key with an advancing counter keeps working`() {
    val authenticator = VirtualAuthenticator(
      backupEligible = false,
      backupState = false,
      incrementCounter = true
    )
    val registration = register(authenticator)

    repeat(3) {
      val login = signIn(authenticator, registration.userHandle)
      assertNotNull(exchange(login.code, login.verifier))
    }
  }

  @Test
  fun `the start call refuses a redirect that is not loopback`() {
    val response = rawPost(
      "/api/v1/auth/game/start",
      startBody("https://evil.example.com/callback", SecureTokens.randomToken())
    )

    assertEquals(400, response.statusCode.value())
  }

  @Test
  fun `a session that has not authenticated cannot be completed`() {
    val session = start()

    val response = rawPost("/api/v1/auth/session/complete", mapOf("session_id" to session.sessionId))

    assertEquals(400, response.statusCode.value())
  }

  @Test
  fun `the login page is refused for an unknown session`() {
    val response = restTemplate.getForEntity(
      "/game-login?session={session}",
      String::class.java,
      SecureTokens.randomToken()
    )

    assertTrue(response.body!!.contains("expired"))
    assertFalse(response.body!!.contains("Use a passkey"))
  }

  @Test
  fun `every response carries the headers that keep the session id out of third party hands`() {
    val response = restTemplate.getForEntity("/game-login?session={s}", String::class.java, "nope")

    assertEquals("no-referrer", response.headers.getFirst("Referrer-Policy"))
    assertEquals("DENY", response.headers.getFirst("X-Frame-Options"))
    assertTrue(response.headers.getFirst("Content-Security-Policy")!!.contains("frame-ancestors 'none'"))
  }

  // --- flow helpers -------------------------------------------------------------------------

  private fun register(
    authenticator: VirtualAuthenticator,
    displayName: String = uniqueDisplayName()
  ): Completed {
    val session = start()

    val options = post(
      "/api/v1/webauthn/register/options",
      mapOf("session_id" to session.sessionId, "display_name" to displayName)
    )

    val publicKey = options.get("public_key")
    val userHandle = decodeB64u(publicKey.get("user").get("id").asText())

    val verified = createCredential(authenticator, options, "/api/v1/webauthn/register/verify")

    return Completed(
      code = completeAndTakeCode(session),
      verifier = session.verifier,
      userHandle = userHandle,
      recoveryCodes = verified.get("recovery_codes").map { it.asText() }
    )
  }

  private fun recover(
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
      )
    )

    val userHandle = decodeB64u(options.get("public_key").get("user").get("id").asText())
    val verified = createCredential(authenticator, options, "/api/v1/webauthn/register/verify")

    return Completed(
      code = completeAndTakeCode(session),
      verifier = session.verifier,
      userHandle = userHandle,
      recoveryCodes = verified.get("recovery_codes").map { it.asText() }
    )
  }

  private fun signIn(authenticator: VirtualAuthenticator, userHandle: ByteArray): Completed {
    val session = start()
    assertOn(session, authenticator, userHandle)

    return Completed(
      code = completeAndTakeCode(session),
      verifier = session.verifier,
      userHandle = userHandle,
      recoveryCodes = emptyList()
    )
  }

  private fun assertOn(
    session: StartedSession,
    authenticator: VirtualAuthenticator,
    userHandle: ByteArray
  ) {
    val options = post("/api/v1/webauthn/assert/options", mapOf("session_id" to session.sessionId))
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
      mapOf("ceremony_id" to options.get("ceremony_id").asText(), "credential" to mapper.readTree(credential))
    )
  }

  private fun addCredential(session: StartedSession, authenticator: VirtualAuthenticator) {
    val options = post("/api/v1/webauthn/credentials/options", mapOf("session_id" to session.sessionId))

    createCredential(authenticator, options, "/api/v1/webauthn/credentials/verify")
  }

  private fun createCredential(
    authenticator: VirtualAuthenticator,
    options: JsonNode,
    verifyPath: String
  ): JsonNode {
    val credential = authenticator.create(
      webAuthnConfig.rpId,
      options.get("public_key").get("challenge").asText(),
      origin()
    )

    return post(
      verifyPath,
      mapOf("ceremony_id" to options.get("ceremony_id").asText(), "credential" to mapper.readTree(credential))
    )
  }

  private fun assertAssertionRefused(rpId: String?, origin: String) {
    val authenticator = VirtualAuthenticator()
    val registration = register(authenticator)
    val session = start()

    val options = post("/api/v1/webauthn/assert/options", mapOf("session_id" to session.sessionId))

    val credential = authenticator.get(
      rpId ?: webAuthnConfig.rpId,
      options.get("public_key").get("challenge").asText(),
      origin,
      registration.userHandle
    )

    val response = rawPost(
      "/api/v1/webauthn/assert/verify",
      mapOf("ceremony_id" to options.get("ceremony_id").asText(), "credential" to mapper.readTree(credential))
    )

    assertEquals(400, response.statusCode.value())
  }

  private fun start(): StartedSession {
    val verifier = SecureTokens.randomToken()
    val state = SecureTokens.randomToken()

    val response = post(
      "/api/v1/auth/game/start",
      startBody("http://127.0.0.1:$LOOPBACK_PORT/callback", state, verifier)
    )

    assertTrue(response.get("login_url").asText().contains("/game-login"))

    return StartedSession(
      sessionId = response.get("session_id").asText(),
      verifier = verifier,
      state = state
    )
  }

  private fun startBody(
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
   * The browser's last step. The code is only minted here, which is why the sixty second lifetime
   * is not spent while the player reads their recovery codes.
   */
  private fun completeAndTakeCode(session: StartedSession): String {
    val completed = post("/api/v1/auth/session/complete", mapOf("session_id" to session.sessionId))
    val query = UriComponentsBuilder.fromUriString(completed.get("redirect_to").asText()).build().queryParams

    assertEquals(session.state, query.getFirst("state"))

    return query.getFirst("code")!!
  }

  private fun exchange(code: String, verifier: String): String {
    val response = rawExchange(code, verifier)

    assertEquals(200, response.statusCode.value())

    return mapper.readTree(response.body).get("token").asText()
  }

  private fun rawExchange(code: String, verifier: String): ResponseEntity<String> {
    return rawPost("/api/v1/auth/game/exchange", mapOf("code" to code, "code_verifier" to verifier))
  }

  /**
   * Verified the way zone-server verifies it - same secret, same issuer and audience checks - so a
   * token that passes here is one the game socket would actually accept.
   */
  private fun accountIdOf(token: String): Long {
    val key = Keys.hmacShaKeyFor(jwtConfig.secret.toByteArray(StandardCharsets.UTF_8))
    val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload

    assertEquals("login", claims.issuer)
    assertTrue(claims.audience.contains("zone"))
    assertEquals("USER", claims.get("role", String::class.java))

    return claims.subject.toLong()
  }

  // --- transport ----------------------------------------------------------------------------

  private fun post(path: String, body: Any): JsonNode {
    val response = rawPost(path, body)

    assertEquals(200, response.statusCode.value(), "POST $path returned ${response.body}")

    return mapper.readTree(response.body)
  }

  private fun rawPost(path: String, body: Any): ResponseEntity<String> {
    val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    return restTemplate.postForEntity(path, HttpEntity(body, headers), String::class.java)
  }

  private fun origin(): String {
    return webAuthnConfig.origins.first()
  }

  private fun uniqueDisplayName(): String {
    return "Player" + SecureTokens.randomToken(12).filter { it.isLetterOrDigit() }.take(20)
  }

  private fun decodeB64u(value: String): ByteArray {
    return Base64.getUrlDecoder().decode(value)
  }

  private data class StartedSession(
    val sessionId: String,
    val verifier: String,
    val state: String
  )

  private data class Completed(
    val code: String,
    val verifier: String,
    val userHandle: ByteArray,
    val recoveryCodes: List<String>
  )

  companion object {
    /** Never bound in the test: the server only records the redirect target, it never calls it. */
    private const val LOOPBACK_PORT = 49721
    private const val RECOVERY_CODE_COUNT = 10
  }
}
