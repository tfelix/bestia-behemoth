package net.bestia.api.push

import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Message encryption for Web Push, RFC 8291, in the `aes128gcm` content coding of RFC 8188: one record, a fresh
 * ephemeral key and salt per message. The push service only ever sees ciphertext; the browser decrypts it with
 * the keys it handed out when it subscribed.
 */
object WebPushEncryption {

  private const val RECORD_SIZE = 4096
  private const val TAG_BITS = 128
  private const val LAST_RECORD_DELIMITER = 0x02.toByte()

  private val ZERO = byteArrayOf(0)
  private val ONE = byteArrayOf(1)
  private val random = SecureRandom()

  /** The request body for one message to the browser holding [receiverPublic] and [authSecret]. */
  fun encrypt(plaintext: ByteArray, receiverPublic: ByteArray, authSecret: ByteArray): ByteArray {
    val ephemeral = KeyPairGenerator.getInstance("EC").apply { initialize(P256.params, random) }.generateKeyPair()
    val senderPublic = P256.encode(ephemeral.public as ECPublicKey)
    val salt = ByteArray(16).also { random.nextBytes(it) }

    val shared = KeyAgreement.getInstance("ECDH").run {
      init(ephemeral.private)
      doPhase(P256.publicKey(receiverPublic), true)
      generateSecret()
    }

    // RFC 8291 section 3.4: the auth secret and both public keys go into the key, then RFC 8188's derivation.
    val keyInfo = "WebPush: info".toByteArray() + ZERO + receiverPublic + senderPublic
    val ikm = hkdf(salt = authSecret, ikm = shared, info = keyInfo, length = 32)
    val contentKey = hkdf(salt, ikm, "Content-Encoding: aes128gcm".toByteArray() + ZERO, 16)
    val nonce = hkdf(salt, ikm, "Content-Encoding: nonce".toByteArray() + ZERO, 12)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(contentKey, "AES"), GCMParameterSpec(TAG_BITS, nonce))
    val ciphertext = cipher.doFinal(plaintext + LAST_RECORD_DELIMITER)

    return ByteBuffer.allocate(16 + 4 + 1 + senderPublic.size + ciphertext.size)
      .put(salt)
      .putInt(RECORD_SIZE)
      .put(senderPublic.size.toByte())
      .put(senderPublic)
      .put(ciphertext)
      .array()
  }

  /** HKDF-SHA-256 (RFC 5869), for outputs of at most one hash length, which is all Web Push needs. */
  fun hkdf(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
    val prk = hmac(salt, ikm)
    return hmac(prk, info + ONE).copyOf(length)
  }

  private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
    return Mac.getInstance("HmacSHA256").run {
      init(SecretKeySpec(key, "HmacSHA256"))
      doFinal(data)
    }
  }

}
