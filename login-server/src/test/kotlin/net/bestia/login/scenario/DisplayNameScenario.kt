package net.bestia.login.scenario

import net.bestia.login.webauthn.VirtualAuthenticator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Registration and recovery read a display name by the same rules. */
class DisplayNameScenario : BasePasskeyScenario() {

  @Test
  fun `a name that only looks Latin is refused at registration`() {
    val session = start()

    val response = rawPost(
      "/api/v1/webauthn/register/options",
      mapOf("session_id" to session.sessionId, "display_name" to "А" + uniqueDisplayName()),
      session.cookie
    )

    assertEquals(400, response.statusCode.value())
  }

  @Test
  fun `recovery finds the account however its spaces are spelled`() {
    val name = uniqueDisplayName().take(12) + " Bob"
    val owner = register(VirtualAuthenticator(), name)
    val session = start()

    val response = rawPost(
      "/api/v1/webauthn/recover/options",
      mapOf(
        "session_id" to session.sessionId,
        "display_name" to " " + name.replace(" ", "   "),
        "recovery_code" to owner.recoveryCodes.first()
      ),
      session.cookie
    )

    assertEquals(200, response.statusCode.value(), response.body)
  }
}
