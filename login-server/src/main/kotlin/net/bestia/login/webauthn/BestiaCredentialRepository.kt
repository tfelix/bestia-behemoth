package net.bestia.login.webauthn

import com.yubico.webauthn.CredentialRepositoryV2
import com.yubico.webauthn.ToPublicKeyCredentialDescriptor
import net.bestia.login.account.loginmethod.WebAuthnCredentialRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import com.yubico.webauthn.data.ByteArray as WebAuthnBytes

/**
 * Database access for the ceremony library. Everything is keyed off the user handle rather than a
 * username, which is what allows the login flow to be fully usernameless.
 */
@Component
class BestiaCredentialRepository(
  private val credentials: WebAuthnCredentialRepository,
  private val users: WebAuthnUserRepository
) : CredentialRepositoryV2<StoredCredential> {

  /**
   * Feeds `excludeCredentials` during registration, so an authenticator that already holds a
   * credential for this account refuses to mint a second one instead of silently duplicating it.
   */
  @Transactional(readOnly = true)
  override fun getCredentialDescriptorsForUserHandle(
    userHandle: WebAuthnBytes
  ): Set<ToPublicKeyCredentialDescriptor> {
    val user = users.findByUserHandle(userHandle.bytes)
      ?: return emptySet()

    return credentials.findAllByAccountId(user.accountId)
      .map { StoredCredential.of(it, user.userHandle) }
      .toSet()
  }

  /**
   * Both halves must match. Resolving on the credential id alone would let an assertion made with
   * one account's credential be accepted while claiming another account's handle.
   */
  @Transactional(readOnly = true)
  override fun lookup(
    credentialId: WebAuthnBytes,
    userHandle: WebAuthnBytes
  ): Optional<StoredCredential> {
    val user = users.findByUserHandle(userHandle.bytes)
      ?: return Optional.empty()

    val credential = credentials.findByCredentialId(credentialId.bytes)
      ?: return Optional.empty()

    if (credential.account.id != user.accountId) {
      return Optional.empty()
    }

    return Optional.of(StoredCredential.of(credential, user.userHandle))
  }

  @Transactional(readOnly = true)
  override fun credentialIdExists(credentialId: WebAuthnBytes): Boolean {
    return credentials.existsByCredentialId(credentialId.bytes)
  }
}
