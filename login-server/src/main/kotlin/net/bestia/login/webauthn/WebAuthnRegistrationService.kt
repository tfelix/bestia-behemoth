package net.bestia.login.webauthn

import com.yubico.webauthn.FinishRegistrationOptions
import com.yubico.webauthn.RelyingPartyV2
import com.yubico.webauthn.StartRegistrationOptions
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import com.yubico.webauthn.data.ResidentKeyRequirement
import com.yubico.webauthn.data.UserIdentity
import com.yubico.webauthn.data.UserVerificationRequirement
import com.yubico.webauthn.exception.RegistrationFailedException
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.Role
import net.bestia.login.account.Account
import net.bestia.login.account.AccountConfig
import net.bestia.login.account.AccountRepository
import net.bestia.login.account.loginmethod.WebAuthnCredential
import net.bestia.login.account.loginmethod.WebAuthnCredentialRepository
import net.bestia.login.gamelogin.GameLoginConfig
import net.bestia.login.recovery.RecoveryCodeService
import net.bestia.login.util.SecureTokens
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import com.yubico.webauthn.data.ByteArray as WebAuthnBytes

@Service
class WebAuthnRegistrationService(
  private val relyingParty: RelyingPartyV2<StoredCredential>,
  private val ceremonyStore: CeremonyStore,
  private val accounts: AccountRepository,
  private val users: WebAuthnUserRepository,
  private val credentials: WebAuthnCredentialRepository,
  private val recoveryCodeService: RecoveryCodeService,
  private val webAuthnConfig: WebAuthnConfig,
  private val gameLoginConfig: GameLoginConfig,
  private val accountConfig: AccountConfig
) {

  /**
   * Begins creating a brand new account. Nothing is written to `account` yet: doing so before the
   * credential verifies would make this an unauthenticated way to fill the table with names.
   */
  @Transactional
  fun startAccountRegistration(displayName: String, loginSessionIdHash: String?): StartedCeremony {
    val name = normalizeDisplayName(displayName)

    if (accounts.existsByDisplayName(name)) {
      throw WebAuthnException("Display name is taken")
    }

    val userHandle = SecureTokens.randomBytes(USER_HANDLE_BYTES)
    val request = buildRequest(name, userHandle)

    val ceremonyId = ceremonyStore.startRegistration(
      request = request,
      loginSessionIdHash = loginSessionIdHash,
      pendingName = name,
      pendingHandle = userHandle,
      accountId = null
    )

    return StartedCeremony(ceremonyId, request)
  }

  /**
   * Begins adding another passkey or security key to an account that already exists.
   *
   * [reissueRecoveryCodes] is set by the recovery path, where the whole point is that the old set
   * is spent and has to be replaced once the replacement credential verifies.
   */
  @Transactional
  fun startCredentialRegistration(
    accountId: Long,
    loginSessionIdHash: String? = null,
    reissueRecoveryCodes: Boolean = false
  ): StartedCeremony {
    val account = accounts.findById(accountId).orElseThrow {
      WebAuthnException("No such account")
    }

    val user = users.findById(accountId).orElseThrow {
      WebAuthnException("Account has no WebAuthn identity")
    }

    val request = buildRequest(account.displayName ?: FALLBACK_DISPLAY_NAME, user.userHandle)

    val ceremonyId = ceremonyStore.startRegistration(
      request = request,
      loginSessionIdHash = loginSessionIdHash,
      pendingName = null,
      pendingHandle = null,
      accountId = accountId,
      reissueRecoveryCodes = reissueRecoveryCodes
    )

    return StartedCeremony(ceremonyId, request)
  }

  /**
   * Verifies the authenticator's response and only then materializes whatever the ceremony was
   * for. Account creation and credential storage share one transaction, so a half-created account
   * cannot survive a failure partway through.
   */
  @Transactional
  fun finishRegistration(ceremonyId: String, credentialJson: String): FinishedRegistration {
    val taken = ceremonyStore.takeRegistration(ceremonyId)

    val response = try {
      PublicKeyCredential.parseRegistrationResponseJson(credentialJson)
    } catch (e: Exception) {
      throw WebAuthnException("Malformed registration response", e)
    }

    val result = try {
      relyingParty.finishRegistration(
        FinishRegistrationOptions.builder()
          .request(taken.request)
          .response(response)
          .build()
      )
    } catch (e: RegistrationFailedException) {
      throw WebAuthnException("Registration ceremony failed", e)
    }

    val ceremony = taken.ceremony
    val pendingName = ceremony.pendingName

    val account: Account
    val userHandle: ByteArray
    val recoveryCodes: List<String>

    if (pendingName != null) {
      val pendingHandle = ceremony.pendingHandle
        ?: throw WebAuthnException("Registration ceremony has a pending name but no handle")

      account = accounts.save(
        Account(role = accountConfig.signUpRole).apply { displayName = pendingName }
      )

      if (accountConfig.signUpRole != Role.USER) {
        LOG.warn {
          "Account ${account.id} ('$pendingName') was created as ${accountConfig.signUpRole}. " +
            "account.sign-up-role is raised, so every registration on this host is elevated."
        }
      }

      users.save(WebAuthnUser(accountId = account.id, userHandle = pendingHandle))
      userHandle = pendingHandle
      recoveryCodes = recoveryCodeService.reissue(account.id, gameLoginConfig.recoveryCodeCount)
    } else {
      val accountId = ceremony.accountId
        ?: throw WebAuthnException("Registration ceremony targets neither a new nor an existing account")

      account = accounts.findById(accountId).orElseThrow {
        WebAuthnException("No such account")
      }
      userHandle = users.findById(accountId).orElseThrow {
        WebAuthnException("Account has no WebAuthn identity")
      }.userHandle
      recoveryCodes = if (ceremony.reissueRecoveryCodes) {
        recoveryCodeService.reissue(accountId, gameLoginConfig.recoveryCodeCount)
      } else {
        emptyList()
      }
    }

    val stored = credentials.save(
      WebAuthnCredential(
        account = account,
        credentialId = result.keyId.id.bytes,
        publicKeyCose = result.publicKeyCose.bytes,
        // BE is only ever reported here. Miss it now and the only way to learn whether the
        // credential syncs is to make the player enrol it again.
        backupEligible = result.isBackupEligible,
        uvInitialized = result.isUserVerified,
        aaguid = result.aaguid.bytes,
        transports = StoredCredential.formatTransports(result.keyId.transports.orElse(null)),
        discoverable = result.isDiscoverable.orElse(null)
      ).apply {
        signatureCount = result.signatureCount
        backupState = result.isBackedUp
        lastUsedAt = LocalDateTime.now()
      }
    )

    LOG.info {
      "Registered credential ${stored.id} for account ${account.id} " +
        "(backupEligible=${stored.backupEligible}, discoverable=${stored.discoverable})"
    }

    return FinishedRegistration(
      accountId = account.id,
      credentialRowId = stored.id,
      userHandle = userHandle,
      recoveryCodes = recoveryCodes,
      loginSessionIdHash = ceremony.loginSessionIdHash
    )
  }

  private fun buildRequest(
    displayName: String,
    userHandle: ByteArray
  ): PublicKeyCredentialCreationOptions {
    val identity = UserIdentity.builder()
      .name(displayName)
      .displayName(displayName)
      .id(WebAuthnBytes(userHandle))
      .build()

    val selection = AuthenticatorSelectionCriteria.builder()
      // A discoverable credential is what makes login usernameless: without it the authenticator
      // cannot find the credential unless we first tell it who to look for.
      .residentKey(ResidentKeyRequirement.REQUIRED)
      // Requiring UV is what makes the passkey two factors rather than one, and is the reason it
      // can stand alone as the account's only credential.
      .userVerification(UserVerificationRequirement.REQUIRED)
      // authenticatorAttachment is left unset on purpose: pinning it to `platform` would hide
      // security keys and the phone-over-QR option from the picker.
      .build()

    // excludeCredentials is not set here. The V2 relying party fills it from
    // getCredentialDescriptorsForUserHandle, which is empty for an account that does not exist yet
    // and complete for one that does.
    return relyingParty.startRegistration(
      StartRegistrationOptions.builder()
        .user(identity)
        .authenticatorSelection(selection)
        .timeout(webAuthnConfig.ceremonyTtlSeconds * MILLIS_PER_SECOND)
        .build()
    )
  }

  private fun normalizeDisplayName(raw: String): String {
    val name = raw.trim()

    if (name.length !in MIN_NAME_LENGTH..MAX_NAME_LENGTH) {
      throw WebAuthnException("Display name must be $MIN_NAME_LENGTH-$MAX_NAME_LENGTH characters")
    }

    if (!name.all { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' }) {
      throw WebAuthnException("Display name contains unsupported characters")
    }

    return name
  }

  data class StartedCeremony(
    val ceremonyId: String,
    val request: PublicKeyCredentialCreationOptions
  )

  data class FinishedRegistration(
    val accountId: Long,
    val credentialRowId: Long,
    val userHandle: ByteArray,
    /** Non-empty when this ceremony created the account, or replaced a spent recovery set. */
    val recoveryCodes: List<String>,
    /** Null for an account-management ceremony, which has no game login waiting on it. */
    val loginSessionIdHash: String?
  )

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** The spec caps the handle at 64 bytes; 32 is already far beyond any collision concern. */
    private const val USER_HANDLE_BYTES = 32
    private const val MIN_NAME_LENGTH = 3
    private const val MAX_NAME_LENGTH = 32
    private const val MILLIS_PER_SECOND = 1000L
    private const val FALLBACK_DISPLAY_NAME = "Bestia player"
  }
}
