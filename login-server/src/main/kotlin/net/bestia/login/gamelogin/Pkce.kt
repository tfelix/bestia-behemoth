package net.bestia.login.gamelogin

import net.bestia.login.util.SecureTokens

/**
 * Proof Key for Code Exchange, RFC 7636.
 *
 * Worth having even though this server is both the authorization server and the only client: the
 * loopback interface is shared with every other process on the machine, so possession of the code
 * alone must not be enough. The verifier never leaves the game process, which is what binds the
 * code to the client that started the session.
 */
object Pkce {

  const val METHOD_S256 = "S256"

  /**
   * `plain` is not accepted. It offers no binding at all, and supporting it would mean an attacker
   * who can pick the challenge can also satisfy it.
   */
  fun verify(codeVerifier: String, storedChallenge: String, method: String): Boolean {
    if (method != METHOD_S256) {
      return false
    }

    if (codeVerifier.length !in MIN_VERIFIER_LENGTH..MAX_VERIFIER_LENGTH) {
      return false
    }

    if (!codeVerifier.all { it in UNRESERVED }) {
      return false
    }

    val computed = SecureTokens.base64Url(SecureTokens.sha256(codeVerifier))

    return SecureTokens.constantTimeEquals(
      computed.toByteArray(Charsets.US_ASCII),
      storedChallenge.toByteArray(Charsets.US_ASCII)
    )
  }

  // RFC 7636 section 4.1.
  private const val MIN_VERIFIER_LENGTH = 43
  private const val MAX_VERIFIER_LENGTH = 128
  private val UNRESERVED = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~')
}
