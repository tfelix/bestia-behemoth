package net.bestia.api.push

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64

/** P-256 keys in the raw forms Web Push uses: a 65-byte uncompressed point, a 32-byte scalar, both base64url. */
object P256 {

  private const val COORDINATE_BYTES = 32
  private const val UNCOMPRESSED = 0x04.toByte()

  val params: ECParameterSpec = AlgorithmParameters.getInstance("EC")
    .apply { init(ECGenParameterSpec("secp256r1")) }
    .getParameterSpec(ECParameterSpec::class.java)

  private val keys = KeyFactory.getInstance("EC")

  fun publicKey(uncompressed: ByteArray): ECPublicKey {
    require(uncompressed.size == 1 + 2 * COORDINATE_BYTES && uncompressed[0] == UNCOMPRESSED) {
      "Not an uncompressed P-256 point (${uncompressed.size} bytes)"
    }
    val x = BigInteger(1, uncompressed.copyOfRange(1, 1 + COORDINATE_BYTES))
    val y = BigInteger(1, uncompressed.copyOfRange(1 + COORDINATE_BYTES, uncompressed.size))

    return keys.generatePublic(ECPublicKeySpec(ECPoint(x, y), params)) as ECPublicKey
  }

  fun privateKey(scalar: ByteArray): ECPrivateKey {
    return keys.generatePrivate(ECPrivateKeySpec(BigInteger(1, scalar), params)) as ECPrivateKey
  }

  fun encode(key: ECPublicKey): ByteArray {
    return byteArrayOf(UNCOMPRESSED) + fixed(key.w.affineX) + fixed(key.w.affineY)
  }

  /** Big-endian, exactly [COORDINATE_BYTES] long: [BigInteger] drops leading zeros and may add a sign byte. */
  fun fixed(value: BigInteger): ByteArray {
    val bytes = value.toByteArray()
    return when {
      bytes.size == COORDINATE_BYTES -> bytes
      bytes.size > COORDINATE_BYTES -> bytes.copyOfRange(bytes.size - COORDINATE_BYTES, bytes.size)
      else -> ByteArray(COORDINATE_BYTES - bytes.size) + bytes
    }
  }

  fun base64Url(bytes: ByteArray): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
  }

  fun fromBase64Url(text: String): ByteArray {
    return Base64.getUrlDecoder().decode(text.trimEnd('='))
  }
}
