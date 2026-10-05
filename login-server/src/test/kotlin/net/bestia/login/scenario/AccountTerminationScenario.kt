package net.bestia.login.scenario

import net.bestia.login.webauthn.VirtualAuthenticator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity

/**
 * A recovery means the owner lost control of their passkeys. Whoever holds one may be half way through a
 * login at that moment, and that login must not finish either.
 */
class AccountTerminationScenario : BasePasskeyScenario() {

  @Test
  fun `a thief's signed-in page cannot finish once the owner recovers`() {
    val displayName = uniqueDisplayName()
    val stolen = VirtualAuthenticator()
    val owner = register(stolen, displayName)
    val thief = start()
    assertOn(thief, stolen, owner.userHandle)

    recover(VirtualAuthenticator(), displayName, owner.recoveryCodes.first())
    val late = rawPost("/api/v1/auth/session/complete", mapOf("session_id" to thief.sessionId), thief.cookie)

    assertEquals(400, late.statusCode.value())
  }

  @Test
  fun `a code the thief already holds cannot be exchanged once the owner recovers`() {
    val displayName = uniqueDisplayName()
    val stolen = VirtualAuthenticator()
    val owner = register(stolen, displayName)
    val thief = start()
    assertOn(thief, stolen, owner.userHandle)
    val code = completeAndTakeCode(thief)

    recover(VirtualAuthenticator(), displayName, owner.recoveryCodes.first())

    assertEquals(400, rawExchange(code, thief.verifier).statusCode.value())
  }

  @Test
  fun `a recovered account no longer accepts the passkeys it had before`() {
    val displayName = uniqueDisplayName()
    val stolen = VirtualAuthenticator()
    val owner = register(stolen, displayName)

    recover(VirtualAuthenticator(), displayName, owner.recoveryCodes.first())

    assertEquals(400, rawAssertOn(start(), stolen, owner.userHandle).statusCode.value())
  }

  @Test
  fun `the passkey made during a recovery signs in`() {
    val displayName = uniqueDisplayName()
    val owner = register(VirtualAuthenticator(), displayName)
    val replacement = VirtualAuthenticator()

    recover(replacement, displayName, owner.recoveryCodes.first())

    assertEquals(200, rawAssertOn(start(), replacement, owner.userHandle).statusCode.value())
  }

  private fun rawAssertOn(
    session: StartedSession,
    authenticator: VirtualAuthenticator,
    userHandle: ByteArray
  ): ResponseEntity<String> {
    val options = post("/api/v1/webauthn/assert/options", mapOf("session_id" to session.sessionId), session.cookie)
    val credential = authenticator.get(
      webAuthnConfig.rpId,
      options.get("public_key").get("challenge").asText(),
      origin(),
      userHandle
    )

    return rawPost(
      "/api/v1/webauthn/assert/verify",
      mapOf("ceremony_id" to options.get("ceremony_id").asText(), "credential" to mapper.readTree(credential)),
      session.cookie
    )
  }
}
