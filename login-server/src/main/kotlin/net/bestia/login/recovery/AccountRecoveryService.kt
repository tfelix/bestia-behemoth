package net.bestia.login.recovery

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.login.account.AccountLoginGuard
import net.bestia.login.account.AccountRepository
import net.bestia.login.gamelogin.RefreshTokenService
import net.bestia.login.webauthn.WebAuthnException
import net.bestia.login.webauthn.WebAuthnRegistrationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The way back in for a player who has lost every passkey.
 *
 * Redeeming a code does not by itself sign anyone in: it opens a registration ceremony, and the
 * account is only reachable again once a new credential has actually been created and verified.
 * That keeps a leaked code from being usable without also completing a WebAuthn ceremony on a real
 * authenticator.
 */
@Service
class AccountRecoveryService(
  private val accounts: AccountRepository,
  private val recoveryCodeService: RecoveryCodeService,
  private val registrationService: WebAuthnRegistrationService,
  private val accountLoginGuard: AccountLoginGuard,
  private val refreshTokenService: RefreshTokenService
) {

  @Transactional
  fun startRecovery(
    displayName: String,
    recoveryCode: String,
    loginSessionIdHash: String
  ): WebAuthnRegistrationService.StartedCeremony {
    val account = accounts.findByDisplayName(displayName.trim())

    // One failure for a wrong name and a wrong code alike, so the endpoint cannot be used to find
    // out which display names exist.
    if (account == null || !recoveryCodeService.redeem(account.id, recoveryCode)) {
      LOG.info { "Rejected recovery attempt for display name of length ${displayName.trim().length}" }
      throw WebAuthnException("Recovery failed")
    }

    accountLoginGuard.denialReason(account)?.let { reason ->
      LOG.info { "Refusing recovery: $reason" }
      throw WebAuthnException("Recovery failed")
    }

    LOG.warn { "Account ${account.id} is being recovered with a recovery code" }

    // A player recovering has lost control of their passkeys, so a client still resuming this account
    // without one is as likely to be whoever took them as the owner. Done on the spent code rather
    // than on the finished ceremony: the code is the part that proves this came from the owner's own
    // records, and waiting would leave the thief's session alive through a recovery that failed.
    refreshTokenService.revokeAllForAccount(account.id)

    return registrationService.startCredentialRegistration(
      accountId = account.id,
      loginSessionIdHash = loginSessionIdHash,
      // The code just spent is gone, and the rest of the set goes with it: a set that has been
      // used once has been out of the player's exclusive control at least that long.
      reissueRecoveryCodes = true
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
