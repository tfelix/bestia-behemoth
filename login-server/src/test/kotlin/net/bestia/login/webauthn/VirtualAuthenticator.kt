package net.bestia.login.webauthn

import com.upokecenter.cbor.CBORObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * A FIDO2 authenticator, in about two hundred lines.
 *
 * Written against the specification rather than against the production code, in the same spirit as
 * [net.bestia.login.eip712.Eip712SignatureFixture]: if this reused the server's own encoding then a
 * bug in that encoding would be reproduced on both sides and the tests would agree with it.
 *
 * Defaults describe a synced passkey - discoverable, user-verified, backup eligible, signature
 * counter pinned at zero - because that is what iCloud Keychain and Google Password Manager
 * actually send. Flip the constructor flags to model a device-bound security key instead.
 */
class VirtualAuthenticator(
  private val backupEligible: Boolean = true,
  private val backupState: Boolean = true,
  private val userVerified: Boolean = true,
  private val userPresent: Boolean = true,
  /** Synced credentials never move this. A hardware key increments on every assertion. */
  private val incrementCounter: Boolean = false
) {

  private val random = SecureRandom()
  private val keyPair: KeyPair = generateKeyPair()

  val credentialId: ByteArray = ByteArray(CREDENTIAL_ID_BYTES).also { random.nextBytes(it) }
  val aaguid: ByteArray = ByteArray(AAGUID_BYTES).also { random.nextBytes(it) }

  private var signatureCounter: Long = 0

  /** The JSON a browser would post back from `navigator.credentials.create()`. */
  fun create(rpId: String, challenge: String, origin: String): String {
    val clientDataJson = clientData("webauthn.create", challenge, origin)
    val authData = authenticatorData(rpId, includeAttestedCredentialData = true)

    // "none": no attestation statement. Requiring a real one would exclude most synced passkey
    // providers, and the relying party is configured to accept untrusted attestation.
    val attestationObject = CBORObject.NewMap()
      .Add("fmt", CBORObject.FromObject("none"))
      .Add("attStmt", CBORObject.NewMap())
      .Add("authData", CBORObject.FromObject(authData))
      .EncodeToBytes()

    return """
      {
        "id": "${b64u(credentialId)}",
        "type": "public-key",
        "rawId": "${b64u(credentialId)}",
        "authenticatorAttachment": "platform",
        "response": {
          "clientDataJSON": "${b64u(clientDataJson)}",
          "attestationObject": "${b64u(attestationObject)}",
          "transports": ["internal", "hybrid"]
        },
        "clientExtensionResults": { "credProps": { "rk": true } }
      }
    """.trimIndent()
  }

  /** The JSON a browser would post back from `navigator.credentials.get()`. */
  fun get(rpId: String, challenge: String, origin: String, userHandle: ByteArray): String {
    if (incrementCounter) {
      signatureCounter++
    }

    val clientDataJson = clientData("webauthn.get", challenge, origin)
    val authData = authenticatorData(rpId, includeAttestedCredentialData = false)

    // The signature covers authenticatorData concatenated with the hash of the client data, which
    // is what binds the assertion to this challenge and this origin.
    val signed = authData + sha256(clientDataJson)
    val signature = Signature.getInstance("SHA256withECDSA").run {
      initSign(keyPair.private)
      update(signed)
      sign()
    }

    return """
      {
        "id": "${b64u(credentialId)}",
        "type": "public-key",
        "rawId": "${b64u(credentialId)}",
        "authenticatorAttachment": "platform",
        "response": {
          "clientDataJSON": "${b64u(clientDataJson)}",
          "authenticatorData": "${b64u(authData)}",
          "signature": "${b64u(signature)}",
          "userHandle": "${b64u(userHandle)}"
        },
        "clientExtensionResults": {}
      }
    """.trimIndent()
  }

  private fun clientData(type: String, challenge: String, origin: String): ByteArray {
    return """{"type":"$type","challenge":"$challenge","origin":"$origin","crossOrigin":false}"""
      .toByteArray(Charsets.UTF_8)
  }

  private fun authenticatorData(rpId: String, includeAttestedCredentialData: Boolean): ByteArray {
    var flags = 0

    if (userPresent) {
      flags = flags or FLAG_UP
    }
    if (userVerified) {
      flags = flags or FLAG_UV
    }
    if (backupEligible) {
      flags = flags or FLAG_BE
    }
    if (backupState) {
      flags = flags or FLAG_BS
    }
    if (includeAttestedCredentialData) {
      flags = flags or FLAG_AT
    }

    val header = sha256(rpId.toByteArray(Charsets.UTF_8)) +
      byteArrayOf(flags.toByte()) +
      counterBytes()

    if (!includeAttestedCredentialData) {
      return header
    }

    return header +
      aaguid +
      byteArrayOf((credentialId.size shr 8).toByte(), (credentialId.size and 0xFF).toByte()) +
      credentialId +
      coseKey()
  }

  private fun counterBytes(): ByteArray {
    return byteArrayOf(
      (signatureCounter shr 24 and 0xFF).toByte(),
      (signatureCounter shr 16 and 0xFF).toByte(),
      (signatureCounter shr 8 and 0xFF).toByte(),
      (signatureCounter and 0xFF).toByte()
    )
  }

  /** COSE_Key for an ES256 key on P-256, as RFC 8152 defines it. */
  private fun coseKey(): ByteArray {
    val publicKey = keyPair.public as ECPublicKey
    val point = publicKey.w

    // Keys are CBOR integers, several of them negative, so they are built explicitly rather than
    // left to an overload that would read a bare int as an array index.
    return CBORObject.NewMap()
      .Add(CBORObject.FromObject(COSE_KTY), CBORObject.FromObject(COSE_KTY_EC2))
      .Add(CBORObject.FromObject(COSE_ALG), CBORObject.FromObject(COSE_ALG_ES256))
      .Add(CBORObject.FromObject(COSE_CRV), CBORObject.FromObject(COSE_CRV_P256))
      .Add(CBORObject.FromObject(COSE_X), CBORObject.FromObject(coordinate(point.affineX.toByteArray())))
      .Add(CBORObject.FromObject(COSE_Y), CBORObject.FromObject(coordinate(point.affineY.toByteArray())))
      .EncodeToBytes()
  }

  /**
   * BigInteger.toByteArray is signed, so it grows a leading zero for values with the high bit set
   * and drops leading zeroes otherwise. COSE wants exactly 32 unsigned bytes.
   */
  private fun coordinate(raw: ByteArray): ByteArray {
    if (raw.size == COORDINATE_BYTES) {
      return raw
    }

    if (raw.size > COORDINATE_BYTES) {
      return raw.copyOfRange(raw.size - COORDINATE_BYTES, raw.size)
    }

    return ByteArray(COORDINATE_BYTES - raw.size) + raw
  }

  private fun generateKeyPair(): KeyPair {
    val generator = KeyPairGenerator.getInstance("EC")
    generator.initialize(ECGenParameterSpec("secp256r1"), random)

    return generator.generateKeyPair()
  }

  private fun sha256(value: ByteArray): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(value)
  }

  private fun b64u(value: ByteArray): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value)
  }

  companion object {
    private const val CREDENTIAL_ID_BYTES = 32
    private const val AAGUID_BYTES = 16
    private const val COORDINATE_BYTES = 32

    // Authenticator data flags, WebAuthn section 6.1.
    private const val FLAG_UP = 0x01
    private const val FLAG_UV = 0x04
    private const val FLAG_BE = 0x08
    private const val FLAG_BS = 0x10
    private const val FLAG_AT = 0x40

    private const val COSE_KTY = 1
    private const val COSE_ALG = 3
    private const val COSE_CRV = -1
    private const val COSE_X = -2
    private const val COSE_Y = -3
    private const val COSE_KTY_EC2 = 2
    private const val COSE_ALG_ES256 = -7
    private const val COSE_CRV_P256 = 1
  }
}
