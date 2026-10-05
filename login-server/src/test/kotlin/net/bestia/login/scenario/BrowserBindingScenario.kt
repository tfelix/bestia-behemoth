package net.bestia.login.scenario

import net.bestia.login.webauthn.VirtualAuthenticator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders

/**
 * Whoever starts a game login learns the session id. Without a binding to the browser that opened the link, an
 * attacker could start a login, send the real link to a victim, and collect the victim's code once the passkey
 * prompt finished - or enrol a passkey of their own on the victim's account.
 */
class BrowserBindingScenario : BasePasskeyScenario() {

  @Test
  fun `the session id alone cannot complete a login another browser authenticated`() {
    val authenticator = VirtualAuthenticator()
    val victim = register(authenticator)
    val session = start()
    assertOn(session, authenticator, victim.userHandle)

    val response = rawPost("/api/v1/auth/session/complete", mapOf("session_id" to session.sessionId))

    assertEquals(400, response.statusCode.value())
  }

  @Test
  fun `the session id alone cannot enrol a passkey on an authenticated session`() {
    val authenticator = VirtualAuthenticator()
    val victim = register(authenticator)
    val session = start()
    assertOn(session, authenticator, victim.userHandle)

    val response = rawPost("/api/v1/webauthn/credentials/options", mapOf("session_id" to session.sessionId))

    assertEquals(400, response.statusCode.value())
  }

  @Test
  fun `a login link cannot be opened by a second browser`() {
    val session = start()

    val secondBrowser = openLoginPage(session.loginPath)

    assertTrue(secondBrowser.body!!.contains("expired"))
    assertFalse(secondBrowser.body!!.contains("Use a passkey"))
  }

  @Test
  fun `the binding cookie is out of reach of scripts and of other sites`() {
    val session = start()

    val page = openLoginPage(session.loginPath, session.cookie)
    val setCookie = page.headers[HttpHeaders.SET_COOKIE]?.firstOrNull { it.startsWith("$BINDING_COOKIE=") }

    assertNotNull(setCookie, "the page sets the binding cookie")
    assertTrue(setCookie!!.contains("HttpOnly"))
    assertTrue(setCookie.contains("SameSite=Strict"))
  }
}
