package net.bestia.login.gamelogin

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.login.util.SecureTokens
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class AuthorizationCodeService(
  private val codes: AuthorizationCodeRepository,
  private val config: GameLoginConfig
) {

  /** Returns the raw code. It is the only time it exists outside the browser's redirect. */
  @Transactional
  fun issue(session: LoginSession, accountId: Long): String {
    val code = SecureTokens.randomToken()

    codes.save(
      AuthorizationCode(
        codeHash = hash(code),
        loginSessionIdHash = session.idHash,
        accountId = accountId,
        expiresAt = LocalDateTime.now().plusSeconds(config.authorizationCodeTtlSeconds)
      )
    )

    return code
  }

  /**
   * Claims the code for this caller, or throws. The claim happens before anything is read back, so
   * a second exchange of the same code loses the race rather than getting a second token.
   */
  @Transactional
  fun consume(rawCode: String): AuthorizationCode {
    val codeHash = hash(rawCode)
    val claimed = codes.consume(codeHash, LocalDateTime.now())

    if (claimed != 1) {
      throw GameLoginException(GameLoginError.INVALID_GRANT, "authorization code is unknown, expired or spent")
    }

    return codes.findById(codeHash).orElseThrow {
      GameLoginException(GameLoginError.INVALID_GRANT, "authorization code vanished after being claimed")
    }
  }

  private fun hash(rawCode: String): String {
    return SecureTokens.base64Url(SecureTokens.sha256(rawCode))
  }

  @Scheduled(fixedDelayString = "PT5M")
  @Transactional
  fun sweepExpired() {
    val removed = codes.deleteExpired(LocalDateTime.now())
    if (removed > 0) {
      LOG.debug { "Swept $removed expired authorization codes" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
