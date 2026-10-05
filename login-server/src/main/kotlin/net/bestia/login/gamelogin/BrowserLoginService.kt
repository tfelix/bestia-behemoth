package net.bestia.login.gamelogin

import net.bestia.login.account.AccountLoginGuard
import net.bestia.login.account.AccountRepository
import net.bestia.login.util.SecureTokens
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

/**
 * The browser half of a game login, shared by every authentication method. A method checks the session with
 * [requireUsable] before its ceremony and hands the account it proved to [authenticate] after it. The browser
 * binding and the account checks stay here, so a new method only has to prove an account.
 */
@Service
class BrowserLoginService(
  private val loginSessions: LoginSessionService,
  private val sessions: LoginSessionRepository,
  private val accounts: AccountRepository,
  private val accountLoginGuard: AccountLoginGuard
) {

  /**
   * Binds the browser half of the login to the browser that opens the link first, and answers the cookie value
   * that proves it. The same browser loading the page again keeps its value; any other browser gets null.
   *
   * Always minted here and never taken from the request, so a cookie planted in the victim's browser cannot
   * pre-claim a session.
   */
  @Transactional
  fun claim(sessionId: String, presentedBinding: String?): String? {
    val session = loginSessions.requireUsable(sessionId)

    if (session.browserBindingHash != null) {
      return presentedBinding?.takeIf { isBoundTo(session, it) }
    }

    val binding = SecureTokens.randomToken()
    val claimed = sessions.claimBrowser(session.idHash, hash(binding), LocalDateTime.now())

    return if (claimed == 1) binding else null
  }

  @Transactional(readOnly = true)
  fun requireUsable(sessionId: String, browserBinding: String?): LoginSession {
    val session = loginSessions.requireUsable(sessionId)
    requireBoundTo(session, browserBinding)

    return session
  }

  /** Authenticated but not yet redeemed: what authorizes every step up to the return to the game. */
  @Transactional(readOnly = true)
  fun requireAuthenticated(sessionId: String, browserBinding: String?): LoginSession {
    return requireAuthenticatedByHash(loginSessions.hash(sessionId), browserBinding)
  }

  @Transactional(readOnly = true)
  fun requireAuthenticatedByHash(sessionIdHash: String, browserBinding: String?): LoginSession {
    val session = loginSessions.findByHash(sessionIdHash)
      ?: throw GameLoginException(GameLoginError.INVALID_GRANT, "no such login session")

    requireBoundTo(session, browserBinding)

    if (!session.isAuthenticated(LocalDateTime.now())) {
      throw GameLoginException(GameLoginError.INVALID_GRANT, "login session is ${session.status} or expired")
    }

    return session
  }

  /**
   * Signs the login session in as the account a method proved. The session comes from the method's own
   * server-side state, never from the request, so a proof from one login cannot be attached to another.
   */
  @Transactional
  fun authenticate(sessionIdHash: String, accountId: Long, browserBinding: String?) {
    val session = loginSessions.findByHash(sessionIdHash)
      ?: throw GameLoginException(GameLoginError.INVALID_GRANT, "login session no longer exists")

    requireBoundTo(session, browserBinding)

    val account = accounts.findById(accountId).orElseThrow {
      GameLoginException(GameLoginError.INVALID_GRANT, "authenticated account no longer exists")
    }

    accountLoginGuard.denialReason(account)?.let { reason ->
      throw GameLoginException(GameLoginError.ACCOUNT_UNAVAILABLE, reason)
    }

    loginSessions.markAuthenticated(session, accountId)
  }

  private fun requireBoundTo(session: LoginSession, browserBinding: String?) {
    if (browserBinding == null || !isBoundTo(session, browserBinding)) {
      throw GameLoginException(GameLoginError.INVALID_GRANT, "login session is not bound to this browser")
    }
  }

  private fun isBoundTo(session: LoginSession, browserBinding: String): Boolean {
    val expected = session.browserBindingHash ?: return false

    return SecureTokens.constantTimeEquals(
      expected.toByteArray(StandardCharsets.UTF_8),
      hash(browserBinding).toByteArray(StandardCharsets.UTF_8)
    )
  }

  private fun hash(browserBinding: String): String {
    return SecureTokens.base64Url(SecureTokens.sha256(browserBinding))
  }

  companion object {
    /** Set by the login page and sent back by its scripts; see [claim]. */
    const val BINDING_COOKIE = "bestia_login_binding"
  }
}
