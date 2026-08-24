package net.bestia.login.gamelogin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RedirectUriValidatorTest {

  private val validator = RedirectUriValidator()

  @Test
  fun `accepts an ipv4 loopback redirect`() {
    val uri = validator.validate("http://127.0.0.1:49721/callback")

    assertEquals("127.0.0.1", uri.host)
    assertEquals(49721, uri.port)
  }

  @Test
  fun `accepts an ipv6 loopback redirect`() {
    val uri = validator.validate("http://[::1]:49721/callback")

    assertEquals(49721, uri.port)
  }

  /**
   * The important case. Without it the login server becomes an open redirector that will mail an
   * authorization code to any host the untrusted client names.
   */
  @ParameterizedTest
  @ValueSource(
    strings = [
      "http://evil.example.com:49721/callback",
      "https://evil.example.com/callback",
      "http://10.0.0.5:49721/callback",
      "http://127.0.0.1.evil.com:49721/callback"
    ]
  )
  fun `rejects a redirect that is not loopback`(redirectUri: String) {
    assertRejected(redirectUri)
  }

  /**
   * "localhost" is a name. A hosts file entry or a DNS search domain can point it off this machine,
   * and then the loopback guarantee is gone.
   */
  @Test
  fun `rejects localhost by name`() {
    assertRejected("http://localhost:49721/callback")
  }

  @Test
  fun `rejects https because the loopback exception is http only`() {
    assertRejected("https://127.0.0.1:49721/callback")
  }

  @Test
  fun `rejects a privileged port`() {
    assertRejected("http://127.0.0.1:80/callback")
  }

  @Test
  fun `rejects a missing port`() {
    assertRejected("http://127.0.0.1/callback")
  }

  @Test
  fun `rejects a redirect that already carries a query or fragment`() {
    assertRejected("http://127.0.0.1:49721/callback?next=x")
    assertRejected("http://127.0.0.1:49721/callback#x")
  }

  @Test
  fun `rejects embedded user info`() {
    assertRejected("http://user@127.0.0.1:49721/callback")
  }

  @Test
  fun `rejects an over long redirect`() {
    assertRejected("http://127.0.0.1:49721/" + "a".repeat(300))
  }

  private fun assertRejected(redirectUri: String) {
    val thrown = assertThrows(GameLoginException::class.java) {
      validator.validate(redirectUri)
    }

    assertEquals(GameLoginError.INVALID_REQUEST, thrown.error)
  }
}
