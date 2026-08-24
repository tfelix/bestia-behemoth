package net.bestia.login.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Random identifiers that leave the server, and the digests under which they are stored.
 *
 * Nothing in the login flow persists a token in the clear. Session ids, authorization codes and
 * recovery codes are all looked up by SHA-256, so a copy of the database yields hashes of secrets
 * that have already expired rather than anything replayable. SHA-256 with no work factor is the
 * right choice here precisely because these are full-entropy random values and not passwords -
 * there is no dictionary to slow an attacker down against.
 */
object SecureTokens {

  private val RANDOM = SecureRandom()
  private val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

  fun randomBytes(length: Int): ByteArray {
    val bytes = ByteArray(length)
    RANDOM.nextBytes(bytes)

    return bytes
  }

  /** 32 bytes by default, i.e. 256 bits, rendered as 43 base64url characters. */
  fun randomToken(byteLength: Int = 32): String {
    return ENCODER.encodeToString(randomBytes(byteLength))
  }

  fun sha256(value: ByteArray): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(value)
  }

  fun sha256(value: String): ByteArray {
    return sha256(value.toByteArray(StandardCharsets.UTF_8))
  }

  fun base64Url(value: ByteArray): String {
    return ENCODER.encodeToString(value)
  }

  /** Constant time, for the rare comparison that is not already a keyed database lookup. */
  fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    return MessageDigest.isEqual(a, b)
  }
}
