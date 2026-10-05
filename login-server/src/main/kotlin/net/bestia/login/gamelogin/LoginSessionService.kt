package net.bestia.login.gamelogin

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.login.util.SecureTokens
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

/**
 * A game login from the game's side: started with PKCE, authenticated by whichever method the player uses, and
 * redeemed for one authorization code. [BrowserLoginService] holds the browser half.
 */
@Service
class LoginSessionService(
  private val sessions: LoginSessionRepository,
  private val redirectUriValidator: RedirectUriValidator,
  private val config: GameLoginConfig
) {

  @Transactional
  fun start(
    redirectUri: String,
    codeChallenge: String,
    challengeMethod: String,
    clientState: String,
    intent: LoginIntent
  ): StartedSession {
    redirectUriValidator.validate(redirectUri)

    if (challengeMethod != Pkce.METHOD_S256) {
      throw GameLoginException(
        GameLoginError.INVALID_REQUEST,
        "unsupported code_challenge_method '$challengeMethod'"
      )
    }

    requireOpaque(codeChallenge, CHALLENGE_LENGTH, "code_challenge")
    requireOpaque(clientState, STATE_MAX_LENGTH, "state")

    val sessionId = SecureTokens.randomToken()

    sessions.save(
      LoginSession(
        idHash = hash(sessionId),
        redirectUri = redirectUri,
        codeChallenge = codeChallenge,
        challengeMethod = challengeMethod,
        clientState = clientState,
        expiresAt = LocalDateTime.now().plusSeconds(config.sessionTtlSeconds)
      )
    )

    val encodedSession = URLEncoder.encode(sessionId, StandardCharsets.UTF_8)
    val loginUrl = "${config.publicBaseUrl.trimEnd('/')}/game-login" +
      "?session=$encodedSession&intent=${intent.name.lowercase()}"

    return StartedSession(
      sessionId = sessionId,
      loginUrl = loginUrl,
      expiresInSeconds = config.sessionTtlSeconds
    )
  }

  @Transactional(readOnly = true)
  fun requireUsable(sessionId: String): LoginSession {
    val session = sessions.findById(hash(sessionId)).orElse(null)
      ?: throw GameLoginException(GameLoginError.INVALID_GRANT, "no such login session")

    if (!session.isUsable(LocalDateTime.now())) {
      throw GameLoginException(
        GameLoginError.INVALID_GRANT,
        "login session is ${session.status} and expires at ${session.expiresAt}"
      )
    }

    return session
  }

  @Transactional(readOnly = true)
  fun findByHash(idHash: String): LoginSession? {
    return sessions.findById(idHash).orElse(null)
  }

  /**
   * Only from PENDING: several ceremonies can be open on one session, and a late one must neither revive a
   * consumed session nor rebind an authenticated one to another account.
   */
  @Transactional
  fun markAuthenticated(session: LoginSession, accountId: Long) {
    if (sessions.authenticate(session.idHash, accountId, LocalDateTime.now()) != 1) {
      throw GameLoginException(GameLoginError.INVALID_GRANT, "login session is ${session.status}, not PENDING")
    }
  }

  /** One ceremony, one code: a second call for the same session is refused. */
  @Transactional
  fun markCodeIssued(idHash: String) {
    advance(idHash, LoginSessionStatus.AUTHENTICATED, LoginSessionStatus.CODE_ISSUED)
  }

  @Transactional
  fun markConsumed(idHash: String) {
    advance(idHash, LoginSessionStatus.CODE_ISSUED, LoginSessionStatus.CONSUMED)
  }

  private fun advance(idHash: String, from: LoginSessionStatus, to: LoginSessionStatus) {
    if (sessions.advance(idHash, from, to, LocalDateTime.now()) != 1) {
      throw GameLoginException(GameLoginError.INVALID_GRANT, "login session is not $from")
    }
  }

  fun hash(sessionId: String): String {
    return SecureTokens.base64Url(SecureTokens.sha256(sessionId))
  }

  @Scheduled(fixedDelayString = "PT5M")
  @Transactional
  fun sweepExpired() {
    val removed = sessions.deleteExpired(LocalDateTime.now())
    if (removed > 0) {
      LOG.debug { "Swept $removed expired login sessions" }
    }
  }

  /**
   * Rejects anything that is not an opaque base64url token of the expected size. These values are
   * echoed into a URL and stored, so bounding them keeps a caller from using the login server as
   * free storage or smuggling delimiters into the redirect.
   */
  private fun requireOpaque(value: String, expectedLength: IntRange, field: String) {
    if (value.length !in expectedLength) {
      throw GameLoginException(GameLoginError.INVALID_REQUEST, "$field has invalid length ${value.length}")
    }

    if (!value.all(::isBase64Url)) {
      throw GameLoginException(GameLoginError.INVALID_REQUEST, "$field is not base64url")
    }
  }

  /** Not `isLetterOrDigit`, which takes any Unicode letter or digit, too. */
  private fun isBase64Url(c: Char): Boolean {
    return c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'
  }

  data class StartedSession(
    val sessionId: String,
    val loginUrl: String,
    val expiresInSeconds: Long
  )

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** SHA-256 rendered as unpadded base64url is always 43 characters. */
    private val CHALLENGE_LENGTH = 43..43
    private val STATE_MAX_LENGTH = 8..128
  }
}
