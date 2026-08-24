package net.bestia.login.gamelogin

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.login.util.SecureTokens
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

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

  /**
   * A session that has passed WebAuthn but has not yet been redeemed. This is what authorizes the
   * "add another passkey" step: the browser proved possession of a credential moments ago, and the
   * session identifier it presents is a 256-bit secret only that tab holds.
   */
  @Transactional(readOnly = true)
  fun requireAuthenticated(sessionId: String): LoginSession {
    val session = sessions.findById(hash(sessionId)).orElse(null)
      ?: throw GameLoginException(GameLoginError.INVALID_GRANT, "no such login session")

    if (session.status != LoginSessionStatus.AUTHENTICATED || session.accountId == null) {
      throw GameLoginException(GameLoginError.INVALID_GRANT, "login session is ${session.status}")
    }

    if (session.expiresAt.isBefore(LocalDateTime.now())) {
      throw GameLoginException(GameLoginError.INVALID_GRANT, "login session expired at ${session.expiresAt}")
    }

    return session
  }

  @Transactional(readOnly = true)
  fun findByHash(idHash: String): LoginSession? {
    return sessions.findById(idHash).orElse(null)
  }

  @Transactional
  fun markAuthenticated(session: LoginSession, accountId: Long) {
    session.status = LoginSessionStatus.AUTHENTICATED
    session.accountId = accountId
    sessions.save(session)
  }

  @Transactional
  fun markConsumed(idHash: String) {
    val session = sessions.findById(idHash).orElse(null) ?: return
    session.status = LoginSessionStatus.CONSUMED
    sessions.save(session)
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

    if (!value.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
      throw GameLoginException(GameLoginError.INVALID_REQUEST, "$field is not base64url")
    }
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
