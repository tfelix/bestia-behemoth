package net.bestia.login.account

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.account.KickReason
import net.bestia.login.gamelogin.AuthorizationCodeRepository
import net.bestia.login.gamelogin.LoginSessionRepository
import net.bestia.login.gamelogin.RefreshTokenService
import net.bestia.login.webauthn.WebAuthnCeremonyRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Ends every way into an account that does not need a new passkey ceremony: standing refresh tokens, a browser
 * login that already passed its ceremony, a code waiting to be exchanged, and an unfinished passkey enrolment.
 *
 * One place for all of them, because each caller - a recovery, a ban - has to close every one, and a list kept
 * at each call site is a list that misses the next way in.
 */
@Service
class AccountSessionTerminator(
  private val refreshTokenService: RefreshTokenService,
  private val loginSessions: LoginSessionRepository,
  private val authorizationCodes: AuthorizationCodeRepository,
  private val ceremonies: WebAuthnCeremonyRepository,
  private val events: ApplicationEventPublisher
) {

  @Transactional
  fun terminate(accountId: Long, reason: KickReason) {
    val now = LocalDateTime.now()

    refreshTokenService.revokeAllForAccount(accountId)
    val signedIn = loginSessions.deleteSignedInForAccount(accountId)
    val codes = authorizationCodes.burnAllForAccount(accountId, now)
    val enrolments = ceremonies.deleteAllForAccount(accountId)

    LOG.info {
      "Terminated account $accountId ($reason): $signedIn browser login(s), $codes code(s), " +
        "$enrolments passkey ceremony(s)"
    }

    events.publishEvent(AccountSessionsTerminatedEvent(accountId, reason))
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
