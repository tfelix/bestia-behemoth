package net.bestia.login.scenario

import net.bestia.login.webauthn.VirtualAuthenticator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}
