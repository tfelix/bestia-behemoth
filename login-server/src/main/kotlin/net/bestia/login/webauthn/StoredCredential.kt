package net.bestia.login.webauthn

import com.yubico.webauthn.CredentialRecord
import com.yubico.webauthn.data.AuthenticatorTransport
import net.bestia.login.account.loginmethod.WebAuthnCredential
import java.util.Optional
import com.yubico.webauthn.data.ByteArray as WebAuthnBytes

/**
 * Adapts a persisted [WebAuthnCredential] into the shape the ceremony library consumes.
 *
 * Kept apart from the entity so the JPA mapping never has to name the library's `ByteArray`, which
 * shadows `kotlin.ByteArray` and would make every column declaration in the entity ambiguous to
 * read. [credentialRowId] carries the row identity back out of a finished assertion so the counter
 * and backup state can be written without a second lookup.
 */
class StoredCredential(
  val credentialRowId: Long,
  val accountId: Long,
  private val credentialIdBytes: WebAuthnBytes,
  private val userHandleBytes: WebAuthnBytes,
  private val publicKeyCoseBytes: WebAuthnBytes,
  private val storedSignatureCount: Long,
  private val transportSet: Set<AuthenticatorTransport>?,
  private val backupEligible: Boolean,
  private val backedUp: Boolean
) : CredentialRecord {

  override fun getCredentialId(): WebAuthnBytes {
    return credentialIdBytes
  }

  override fun getUserHandle(): WebAuthnBytes {
    return userHandleBytes
  }

  override fun getPublicKeyCose(): WebAuthnBytes {
    return publicKeyCoseBytes
  }

  override fun getSignatureCount(): Long {
    return storedSignatureCount
  }

  override fun getTransports(): Optional<MutableSet<AuthenticatorTransport>> {
    return Optional.ofNullable(transportSet?.toMutableSet())
  }

  override fun isBackupEligible(): Optional<Boolean> {
    return Optional.of(backupEligible)
  }

  override fun isBackedUp(): Optional<Boolean> {
    return Optional.of(backedUp)
  }

  companion object {
    /** Transport hints round-trip through the database as the comma separated list of their ids. */
    fun parseTransports(raw: String?): Set<AuthenticatorTransport>? {
      if (raw.isNullOrBlank()) {
        return null
      }

      return raw.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { AuthenticatorTransport.of(it) }
        .toSet()
    }

    fun formatTransports(transports: Set<AuthenticatorTransport>?): String? {
      if (transports.isNullOrEmpty()) {
        return null
      }

      return transports.joinToString(",") { it.id }
    }

    fun of(credential: WebAuthnCredential, userHandle: ByteArray): StoredCredential {
      return StoredCredential(
        credentialRowId = credential.id,
        accountId = credential.account.id,
        credentialIdBytes = WebAuthnBytes(credential.credentialId),
        userHandleBytes = WebAuthnBytes(userHandle),
        publicKeyCoseBytes = WebAuthnBytes(credential.publicKeyCose),
        storedSignatureCount = credential.signatureCount,
        transportSet = parseTransports(credential.transports),
        backupEligible = credential.backupEligible,
        backedUp = credential.backupState
      )
    }
  }
}
