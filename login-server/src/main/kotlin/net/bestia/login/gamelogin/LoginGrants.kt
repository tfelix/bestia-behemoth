package net.bestia.login.gamelogin

import net.bestia.account.KickReason
import net.bestia.login.account.Account
import net.bestia.login.account.AccountLoginGuard
import net.bestia.login.account.AccountRepository
import net.bestia.login.account.AccountSessionTerminator
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * The two ways a client proves it may have tokens - an authorization code from the browser, or a refresh token -
 * checked once for every kind of client. Each grant is bound to the [LoginClient] it was issued for and is refused
 * for any other, so the companion app's grants never yield a zone token.
 */
@Service
class LoginGrants(
  private val loginSessionService: LoginSessionService,
  private val authorizationCodeService: AuthorizationCodeService,
  private val accounts: AccountRepository,
  private val accountLoginGuard: AccountLoginGuard,
  private val refreshTokenService: RefreshTokenService,
  private val accountSessionTerminator: AccountSessionTerminator,
) {

  /** The account behind [code], once, if [codeVerifier] opens the session's PKCE challenge. */
  fun redeemCode(code: String, codeVerifier: String, client: LoginClient): Account {
    // Claim first. If the same code arrives twice, only one caller gets past this line, and it
    // happens before the verifier is examined so a wrong verifier still burns the code.
    val claimed = authorizationCodeService.consume(code)

    val session = loginSessionService.findByHash(claimed.loginSessionIdHash)
      ?: throw GameLoginException(GameLoginError.INVALID_GRANT, "code has no login session")

    if (session.client != client) {
      throw GameLoginException(GameLoginError.INVALID_GRANT, "code belongs to a ${session.client} login, not $client")
    }

    if (!Pkce.verify(codeVerifier, session.codeChallenge, session.challengeMethod)) {
      throw GameLoginException(GameLoginError.INVALID_GRANT, "PKCE verifier does not match")
    }

    if (session.accountId != claimed.accountId) {
      throw GameLoginException(GameLoginError.INVALID_GRANT, "code and login session name different accounts")
    }

    val account = accounts.findById(claimed.accountId).orElseThrow {
      GameLoginException(GameLoginError.INVALID_GRANT, "code refers to a missing account")
    }

    accountLoginGuard.denialReason(account)?.let { reason ->
      throw GameLoginException(GameLoginError.ACCOUNT_UNAVAILABLE, reason)
    }

    loginSessionService.markConsumed(session.idHash)
    touch(account)
    return account
  }

  /**
   * Spends [rawToken] for its successor. Nothing about the passkey ceremony is repeated, so everything that could
   * have changed since is re-examined: the token may have been revoked, and the account may have been banned.
   */
  fun refresh(rawToken: String, client: LoginClient): Refreshed {
    val rotated = refreshTokenService.rotate(rawToken, client)

    if (rotated !is RefreshTokenService.Rotation.Rotated) {
      throw GameLoginException(GameLoginError.INVALID_GRANT, "refresh token could not be rotated")
    }

    val account = accounts.findById(rotated.accountId).orElseThrow {
      GameLoginException(GameLoginError.INVALID_GRANT, "refresh token refers to a missing account")
    }

    accountLoginGuard.denialReason(account)?.let { reason ->
      // The successor minted a moment ago goes too. An account that may not log in has no business
      // holding a standing invitation to do so on any of its devices.
      accountSessionTerminator.terminate(account.id, KickReason.BANNED)

      throw GameLoginException(GameLoginError.ACCOUNT_UNAVAILABLE, reason)
    }

    touch(account)
    return Refreshed(account, rotated.token)
  }

  private fun touch(account: Account) {
    account.lastLogin = LocalDateTime.now()
    accounts.save(account)
  }

  data class Refreshed(val account: Account, val refreshToken: String)
}
