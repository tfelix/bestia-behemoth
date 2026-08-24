package net.bestia.login.gamelogin

import net.bestia.login.util.SecureTokens
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PkceTest {

  private val verifier = SecureTokens.randomToken()
  private val challenge = SecureTokens.base64Url(SecureTokens.sha256(verifier))

  @Test
  fun `accepts the verifier the challenge was derived from`() {
    assertTrue(Pkce.verify(verifier, challenge, Pkce.METHOD_S256))
  }

  @Test
  fun `rejects a different verifier`() {
    assertFalse(Pkce.verify(SecureTokens.randomToken(), challenge, Pkce.METHOD_S256))
  }

  /**
   * `plain` gives the code no binding at all: anyone who can read the challenge can satisfy it.
   * Accepting it would let a caller downgrade its way past the whole mechanism.
   */
  @Test
  fun `rejects the plain method even when the value matches`() {
    assertFalse(Pkce.verify(verifier, verifier, "plain"))
    assertFalse(Pkce.verify(verifier, challenge, "PLAIN"))
    assertFalse(Pkce.verify(verifier, challenge, ""))
  }

  @Test
  fun `rejects a verifier shorter than RFC 7636 allows`() {
    val short = "abc"

    assertFalse(Pkce.verify(short, SecureTokens.base64Url(SecureTokens.sha256(short)), Pkce.METHOD_S256))
  }

  @Test
  fun `rejects a verifier longer than RFC 7636 allows`() {
    val long = "a".repeat(129)

    assertFalse(Pkce.verify(long, SecureTokens.base64Url(SecureTokens.sha256(long)), Pkce.METHOD_S256))
  }

  @Test
  fun `rejects a verifier with characters outside the unreserved set`() {
    val bad = "a".repeat(42) + "/"

    assertFalse(Pkce.verify(bad, SecureTokens.base64Url(SecureTokens.sha256(bad)), Pkce.METHOD_S256))
  }
}
