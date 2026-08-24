package net.bestia.login.webauthn

import com.yubico.webauthn.AssertionRequest
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.login.util.SecureTokens
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Server side storage for in-flight ceremonies.
 *
 * Reads are destructive: [takeRegistration] and [takeAssertion] delete the row they return, so a
 * challenge can be spent exactly once. Without that, a captured response could be replayed against
 * the same stored challenge until it expired.
 */
@Service
class CeremonyStore(
  private val ceremonies: WebAuthnCeremonyRepository,
  private val config: WebAuthnConfig
) {

  @Transactional
  fun startRegistration(
    request: PublicKeyCredentialCreationOptions,
    loginSessionIdHash: String?,
    pendingName: String?,
    pendingHandle: ByteArray?,
    accountId: Long?,
    reissueRecoveryCodes: Boolean = false
  ): String {
    val id = SecureTokens.randomToken()

    ceremonies.save(
      WebAuthnCeremony(
        id = id,
        ceremonyType = CeremonyType.REGISTRATION,
        requestJson = request.toJson(),
        loginSessionIdHash = loginSessionIdHash,
        pendingName = pendingName,
        pendingHandle = pendingHandle,
        accountId = accountId,
        reissueRecoveryCodes = reissueRecoveryCodes,
        expiresAt = LocalDateTime.now().plusSeconds(config.ceremonyTtlSeconds)
      )
    )

    return id
  }

  @Transactional
  fun startAssertion(request: AssertionRequest, loginSessionIdHash: String?): String {
    val id = SecureTokens.randomToken()

    ceremonies.save(
      WebAuthnCeremony(
        id = id,
        ceremonyType = CeremonyType.ASSERTION,
        requestJson = request.toJson(),
        loginSessionIdHash = loginSessionIdHash,
        expiresAt = LocalDateTime.now().plusSeconds(config.ceremonyTtlSeconds)
      )
    )

    return id
  }

  @Transactional
  fun takeRegistration(ceremonyId: String): Taken<PublicKeyCredentialCreationOptions> {
    val ceremony = take(ceremonyId, CeremonyType.REGISTRATION)

    return Taken(
      ceremony = ceremony,
      request = PublicKeyCredentialCreationOptions.fromJson(ceremony.requestJson)
    )
  }

  @Transactional
  fun takeAssertion(ceremonyId: String): Taken<AssertionRequest> {
    val ceremony = take(ceremonyId, CeremonyType.ASSERTION)

    return Taken(
      ceremony = ceremony,
      request = AssertionRequest.fromJson(ceremony.requestJson)
    )
  }

  private fun take(ceremonyId: String, expectedType: CeremonyType): WebAuthnCeremony {
    val ceremony = ceremonies.findById(ceremonyId).orElse(null)
      ?: throw WebAuthnException("No such ceremony")

    ceremonies.delete(ceremony)

    if (ceremony.ceremonyType != expectedType) {
      throw WebAuthnException("Ceremony $ceremonyId is a ${ceremony.ceremonyType}, not $expectedType")
    }

    if (ceremony.expiresAt.isBefore(LocalDateTime.now())) {
      throw WebAuthnException("Ceremony $ceremonyId expired at ${ceremony.expiresAt}")
    }

    return ceremony
  }

  /**
   * Abandoned ceremonies are the common case - a player opens the browser and closes the tab - so
   * the table needs a sweeper rather than relying on the destructive read.
   */
  @Scheduled(fixedDelayString = "PT5M")
  @Transactional
  fun sweepExpired() {
    val removed = ceremonies.deleteExpired(LocalDateTime.now())
    if (removed > 0) {
      LOG.debug { "Swept $removed expired WebAuthn ceremonies" }
    }
  }

  /** The stored row plus its deserialized request, so callers get the pending fields alongside. */
  data class Taken<T>(
    val ceremony: WebAuthnCeremony,
    val request: T
  )

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
