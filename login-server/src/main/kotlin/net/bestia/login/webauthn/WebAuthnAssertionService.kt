package net.bestia.login.webauthn

import com.yubico.webauthn.AssertionRequest
import com.yubico.webauthn.FinishAssertionOptions
import com.yubico.webauthn.RelyingPartyV2
import com.yubico.webauthn.StartAssertionOptions
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.data.UserVerificationRequirement
import com.yubico.webauthn.exception.AssertionFailedException
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.login.account.loginmethod.WebAuthnCredentialRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class WebAuthnAssertionService(
  private val relyingParty: RelyingPartyV2<StoredCredential>,
  private val ceremonyStore: CeremonyStore,
  private val credentials: WebAuthnCredentialRepository,
  private val config: WebAuthnConfig
) {

  /**
   * Starts a usernameless assertion.
   *
   * Neither `username` nor `userHandle` is set, so the generated options carry an empty
   * `allowCredentials`. That empty list is what makes the browser show its own account picker and
   * offer the cross-device (QR) route instead of silently failing when the credential lives on a
   * phone rather than this machine.
   */
  @Transactional
  fun start(loginSessionIdHash: String?): StartedCeremony {
    val request = relyingParty.startAssertion(
      StartAssertionOptions.builder()
        .userVerification(UserVerificationRequirement.REQUIRED)
        .timeout(config.ceremonyTtlSeconds * MILLIS_PER_SECOND)
        .build()
    )

    val ceremonyId = ceremonyStore.startAssertion(request, loginSessionIdHash)

    return StartedCeremony(ceremonyId, request)
  }

  /**
   * Verifies the assertion and returns the account it belongs to.
   *
   * The account is resolved from the user handle the authenticator returned, never from anything
   * the browser supplied alongside it.
   */
  @Transactional
  fun finish(ceremonyId: String, credentialJson: String): FinishedAssertion {
    val taken = ceremonyStore.takeAssertion(ceremonyId)

    val response = try {
      PublicKeyCredential.parseAssertionResponseJson(credentialJson)
    } catch (e: Exception) {
      throw WebAuthnException("Malformed assertion response", e)
    }

    if (!credentials.existsByCredentialId(response.id.bytes)) {
      throw UnknownCredentialException()
    }

    val result = try {
      relyingParty.finishAssertion(
        FinishAssertionOptions.builder()
          .request(taken.request)
          .response(response)
          .build()
      )
    } catch (e: AssertionFailedException) {
      throw WebAuthnException("Assertion ceremony failed", e)
    }

    if (!result.isSuccess) {
      throw WebAuthnException("Assertion did not succeed")
    }

    val credential = credentials.findById(result.credential.credentialRowId).orElseThrow {
      WebAuthnException("Verified credential is no longer stored")
    }

    // The counter stays at zero forever on synced passkeys, because copies cannot coordinate one.
    // The library has already decided whether the value is acceptable (strictly increasing, or
    // both ends exactly zero); all that is left is to keep the stored value current so the next
    // assertion is judged against it.
    credential.signatureCount = result.signatureCount
    credential.backupState = result.isBackedUp
    credential.lastUsedAt = LocalDateTime.now()
    credentials.save(credential)

    val accountId = result.credential.accountId

    LOG.debug {
      "Assertion accepted for account $accountId via credential ${credential.id} " +
        "(uv=${result.isUserVerified}, backedUp=${result.isBackedUp})"
    }

    return FinishedAssertion(
      accountId = accountId,
      credentialRowId = credential.id,
      loginSessionIdHash = taken.ceremony.loginSessionIdHash
    )
  }

  data class StartedCeremony(
    val ceremonyId: String,
    val request: AssertionRequest
  )

  data class FinishedAssertion(
    val accountId: Long,
    val credentialRowId: Long,
    /** Null for an account-management ceremony, which has no game login waiting on it. */
    val loginSessionIdHash: String?
  )

  companion object {
    private val LOG = KotlinLogging.logger { }
    private const val MILLIS_PER_SECOND = 1000L
  }
}
