package net.bestia.login.scenario

import com.fasterxml.jackson.databind.JsonNode
import net.bestia.login.webauthn.VirtualAuthenticator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity

/**
 * One passkey ceremony is one login: it hands out one code, and that code starts one standing session.
 */
class SessionStateScenario : BasePasskeyScenario() {

  @Test
  fun `a login session hands out a single code`() {
    val authenticator = VirtualAuthenticator()
    val registration = register(authenticator)
    val session = start()
    assertOn(session, authenticator, registration.userHandle)
    completeAndTakeCode(session)

    val again = rawPost("/api/v1/auth/session/complete", mapOf("session_id" to session.sessionId), session.cookie)

    assertEquals(400, again.statusCode.value())
  }

  /** Two assertion ceremonies can be opened on one session; only the first one to finish may bind it. */
  @Test
  fun `a second assertion cannot rebind an authenticated session to another account`() {
    val first = VirtualAuthenticator()
    val second = VirtualAuthenticator()
    val firstAccount = register(first)
    val secondAccount = register(second)
    val session = start()
    val firstOptions = assertOptions(session)
    val secondOptions = assertOptions(session)

    assertEquals(200, verifyAssertion(session, first, firstOptions, firstAccount.userHandle).statusCode.value())
    assertEquals(400, verifyAssertion(session, second, secondOptions, secondAccount.userHandle).statusCode.value())
  }

  @Test
  fun `a consumed session cannot be authenticated again`() {
    val authenticator = VirtualAuthenticator()
    val registration = register(authenticator)
    val session = start()
    val firstOptions = assertOptions(session)
    val secondOptions = assertOptions(session)
    verifyAssertion(session, authenticator, firstOptions, registration.userHandle)
    exchange(completeAndTakeCode(session), session.verifier)

    val late = verifyAssertion(session, authenticator, secondOptions, registration.userHandle)

    assertEquals(400, late.statusCode.value())
  }

  private fun assertOptions(session: StartedSession): JsonNode {
    return post("/api/v1/webauthn/assert/options", mapOf("session_id" to session.sessionId), session.cookie)
  }

  private fun verifyAssertion(
    session: StartedSession,
    authenticator: VirtualAuthenticator,
    options: JsonNode,
    userHandle: ByteArray
  ): ResponseEntity<String> {
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
