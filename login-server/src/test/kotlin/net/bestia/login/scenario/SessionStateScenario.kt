package net.bestia.login.scenario

import net.bestia.login.webauthn.VirtualAuthenticator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
