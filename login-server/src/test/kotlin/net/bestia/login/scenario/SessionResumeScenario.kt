package net.bestia.login.scenario

import net.bestia.login.util.SecureTokens
import net.bestia.login.webauthn.VirtualAuthenticator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Starting the game a second time, which is the case the passkey flow on its own does not cover: the
 * ceremony leaves the client with a zone token that expires in minutes and nothing else, so every
 * launch used to mean another trip through the browser.
 *
 * Every test here reaches its standing session through a real ceremony first, then never touches an
 * authenticator, a browser page or a loopback redirect again.
 */
class SessionResumeScenario : BasePasskeyScenario() {

  @Test
  fun `a stored refresh token starts the game again without the browser`() {
    val registration = register(VirtualAuthenticator())
    val first = exchangeFully(registration.code, registration.verifier)

    val resumed = refresh(first.refreshToken)

    assertEquals(
      accountIdOf(first.token), accountIdOf(resumed.token),
      "resuming must land on the same account the passkey authenticated"
    )
    assertNotEquals(
      first.refreshToken, resumed.refreshToken,
      "the response must carry a successor, otherwise the client has nothing left to store"
    )
  }

  /** The stored token is good for as many launches as the player makes, not just the next one. */
  @Test
  fun `resuming repeatedly keeps working`() {
    val registration = register(VirtualAuthenticator())
    val first = exchangeFully(registration.code, registration.verifier)
    val accountId = accountIdOf(first.token)

    var stored = first.refreshToken

    repeat(3) {
      val resumed = refresh(stored)

      assertEquals(accountId, accountIdOf(resumed.token))
      stored = resumed.refreshToken
    }
  }

  @Test
  fun `refreshing retires the token that was presented`() {
    val registration = register(VirtualAuthenticator())
    val first = exchangeFully(registration.code, registration.verifier)

    val second = refresh(first.refreshToken)

    assertEquals(200, rawRefresh(second.refreshToken).statusCode.value(), "the successor must be usable")

    // Last on purpose: presenting the spent token is read as theft and takes the chain with it, so
    // asserting it earlier would make the line above fail for the right reason at the wrong time.
    assertEquals(
      400, rawRefresh(first.refreshToken).statusCode.value(),
      "a spent token must not keep working, or rotation would buy nothing"
    )
  }

  /**
   * The reason rotation is worth having. A token that turns up after it was spent means two clients
   * hold the same one, and there is no way to tell which of them is the player - so the chain dies
   * and both have to prove themselves with a passkey again.
   */
  @Test
  fun `a token presented after it was spent revokes the whole chain`() {
    val registration = register(VirtualAuthenticator())
    val stolen = exchangeFully(registration.code, registration.verifier).refreshToken

    val successor = refresh(stolen).refreshToken

    assertEquals(400, rawRefresh(stolen).statusCode.value())
    assertEquals(
      400, rawRefresh(successor).statusCode.value(),
      "the successor must die with the chain, otherwise whoever rotated last simply keeps the account"
    )
  }

  /**
   * The other half of that rule: revocation is per chain, not per account. A player with the game on
   * two machines must not be signed out of one because the other was compromised.
   */
  @Test
  fun `one device losing its chain leaves the other device signed in`() {
    val authenticator = VirtualAuthenticator()

    val registration = register(authenticator)
    val desktop = exchangeFully(registration.code, registration.verifier)

    val secondLogin = signIn(authenticator, registration.userHandle)
    val laptop = exchangeFully(secondLogin.code, secondLogin.verifier)

    // Kill the desktop chain the only way a client can: present a spent token.
    val desktopSuccessor = refresh(desktop.refreshToken).refreshToken
    rawRefresh(desktop.refreshToken)

    assertEquals(400, rawRefresh(desktopSuccessor).statusCode.value())
    assertEquals(
      200, rawRefresh(laptop.refreshToken).statusCode.value(),
      "the laptop never left the player's hands and must still resume"
    )
  }

  /**
   * Recovery means the passkeys are gone, which is exactly the situation where a client still able to
   * resume is as likely to be whoever took them as the owner.
   */
  @Test
  fun `a standing session does not survive account recovery`() {
    val displayName = uniqueDisplayName()
    val registration = register(VirtualAuthenticator(), displayName)
    val stored = exchangeFully(registration.code, registration.verifier).refreshToken

    recover(VirtualAuthenticator(), displayName, registration.recoveryCodes.first())

    assertEquals(400, rawRefresh(stored).statusCode.value())
  }

  /** Signing out has to reach the server, or it only hides the token from the machine that had it. */
  @Test
  fun `signing out ends the session on the server`() {
    val registration = register(VirtualAuthenticator())
    val stored = exchangeFully(registration.code, registration.verifier).refreshToken

    assertEquals(204, rawRevoke(stored).statusCode.value())
    assertEquals(400, rawRefresh(stored).statusCode.value())
  }

  /** Nothing in the answer says whether the token was real, so it cannot be used to test guesses. */
  @Test
  fun `signing out with a token that was never issued answers the same way`() {
    assertEquals(204, rawRevoke(SecureTokens.randomToken()).statusCode.value())
  }

  @Test
  fun `an unknown refresh token is refused`() {
    assertEquals(400, rawRefresh(SecureTokens.randomToken()).statusCode.value())
  }
}
