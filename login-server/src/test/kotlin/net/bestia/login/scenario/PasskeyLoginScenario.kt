package net.bestia.login.scenario

import net.bestia.login.util.SecureTokens
import net.bestia.login.webauthn.VirtualAuthenticator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The ceremony itself: who a credential resolves to, what a recovery code is worth, and the ways an
 * assertion or a code must not be reusable.
 *
 * Resuming a session afterwards is [SessionResumeScenario]; the plumbing both share is in
 * [BasePasskeyScenario].
 */
class PasskeyLoginScenario : BasePasskeyScenario() {

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
}
