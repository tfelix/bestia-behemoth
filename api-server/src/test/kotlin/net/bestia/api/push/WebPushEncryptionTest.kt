package net.bestia.api.push

import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** What the server encrypts, a browser decrypts: the receiving half of RFC 8291, written out independently. */
class WebPushEncryptionTest {

  @Test
  fun `a browser holding the subscription keys reads the message back`() {
    val browser = KeyPairGenerator.getInstance("EC").apply { initialize(P256.params) }.generateKeyPair()
    val browserPublic = P256.encode(browser.public as ECPublicKey)
    val authSecret = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val message = """{"title":"Ashfen has fallen"}""".toByteArray()

    val body = WebPushEncryption.encrypt(message, browserPublic, authSecret)

    val buffer = ByteBuffer.wrap(body)
    val salt = ByteArray(16).also { buffer.get(it) }
    assertEquals(4096, buffer.getInt())
    val senderPublic = ByteArray(buffer.get().toInt()).also { buffer.get(it) }
    val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }

    val shared = KeyAgreement.getInstance("ECDH").run {
      init(browser.private)
      doPhase(P256.publicKey(senderPublic), true)
      generateSecret()
    }
    val ikm = WebPushEncryption.hkdf(authSecret, shared, "WebPush: info".toByteArray() + 0 + browserPublic + senderPublic, 32)
    val key = WebPushEncryption.hkdf(salt, ikm, "Content-Encoding: aes128gcm".toByteArray() + 0, 16)
    val nonce = WebPushEncryption.hkdf(salt, ikm, "Content-Encoding: nonce".toByteArray() + 0, 12)

    val padded = Cipher.getInstance("AES/GCM/NoPadding").run {
      init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
      doFinal(ciphertext)
    }

    assertEquals(0x02.toByte(), padded.last())
    assertContentEquals(message, padded.copyOf(padded.size - 1))
  }

  private operator fun ByteArray.plus(byte: Int): ByteArray {
    return this + byteArrayOf(byte.toByte())
  }
}
