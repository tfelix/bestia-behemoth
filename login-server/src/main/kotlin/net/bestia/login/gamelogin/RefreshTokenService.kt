package net.bestia.login.gamelogin

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.login.util.SecureTokens
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Issues and rotates the tokens that let a client skip the browser on its next start.
 *
 * The expiry slides: a successor gets a full lifetime rather than inheriting the expiry of the token
 * it replaces, so a player who keeps playing is never sent back to the passkey ceremony. The bound
 * on a stolen token is not its age but the replay check below - as soon as the real client refreshes,
 * one of the two presents a spent token and the family dies for both.
 */
@Service
class RefreshTokenService(
  private val tokens: RefreshTokenRepository,
  private val config: GameLoginConfig
) {

  sealed interface Rotation {

    data class Rotated(
      val token: String,
      val accountId: Long
    ) : Rotation

    /**
     * Unknown, expired, revoked or already spent. Which of those it was stays in the log: telling
     * the caller apart would let it probe the table, and the client's response is the same either
     * way - throw the token away and open the browser.
     */
    data object Refused : Rotation
  }

  /** Starts a new family. Returns the raw token, which exists in that form only here and in transit. */
  @Transactional
  fun issueForNewSession(accountId: Long): String {
    return issue(accountId, SecureTokens.randomToken(FAMILY_ID_BYTES))
  }

  /**
   * Spends the presented token and hands back its successor.
   *
   * Deliberately returns a [Rotation] instead of throwing on refusal: the replay branch *writes* -
   * it revokes the family - and an exception thrown from inside this transaction would leave whether
   * that revocation survives resting on Spring's default rollback rules.
   */
  @Transactional
  fun rotate(rawToken: String): Rotation {
    val now = LocalDateTime.now()
    val tokenHash = hash(rawToken)

    if (tokens.consume(tokenHash, now) != 1) {
      return refuse(tokenHash, now)
    }

    val consumed = tokens.findById(tokenHash).orElse(null)
      ?: return Rotation.Refused

    return Rotation.Rotated(
      token = issue(consumed.accountId, consumed.familyId),
      accountId = consumed.accountId
    )
  }

  /**
   * Ends every session the account has. Used when the account may no longer log in at all, and when
   * a recovery code has been spent - a player recovering has lost control of their passkeys, so any
   * client still holding a token for this account is as likely to be the thief as the owner.
   */
  @Transactional
  fun revokeAllForAccount(accountId: Long): Int {
    val revoked = tokens.revokeAllForAccount(accountId, LocalDateTime.now())

    if (revoked > 0) {
      LOG.info { "Revoked $revoked refresh token(s) for account $accountId" }
    }

    return revoked
  }

  /**
   * Ends the one chain the presented token belongs to, and reports whether it named anything. Used by
   * a client signing out, which knows its own token and has no business ending the sessions of the
   * player's other machines.
   */
  @Transactional
  fun revokeFamilyOf(rawToken: String): Boolean {
    val presented = tokens.findById(hash(rawToken)).orElse(null) ?: return false
    val revoked = tokens.revokeFamily(presented.familyId, LocalDateTime.now())

    LOG.info { "Signed out account ${presented.accountId}, revoking $revoked token(s) in one chain" }

    return true
  }

  @Transactional(readOnly = true)
  fun usableCountFor(accountId: Long): Long {
    return tokens.countByAccountIdAndRevokedAtIsNullAndConsumedAtIsNull(accountId)
  }

  private fun issue(accountId: Long, familyId: String): String {
    val token = SecureTokens.randomToken()

    tokens.save(
      RefreshToken(
        tokenHash = hash(token),
        accountId = accountId,
        familyId = familyId,
        expiresAt = LocalDateTime.now().plusDays(config.refreshTokenDays)
      )
    )

    return token
  }

  /**
   * A token that exists but could not be claimed because it was already spent is the one case worth
   * acting on: the legitimate client rotated it, so whoever is presenting it now kept a copy. Which
   * of the two is the thief is unknowable, so the family goes and both sign in again.
   */
  private fun refuse(tokenHash: String, now: LocalDateTime): Rotation {
    val presented = tokens.findById(tokenHash).orElse(null)

    if (presented == null) {
      LOG.debug { "Refused an unknown refresh token" }
      return Rotation.Refused
    }

    if (presented.consumedAt != null && presented.revokedAt == null) {
      val revoked = tokens.revokeFamily(presented.familyId, now)
      LOG.warn {
        "Refresh token for account ${presented.accountId} was presented after being spent - " +
                "revoked $revoked token(s) in its family"
      }
    } else {
      LOG.debug { "Refused a refresh token for account ${presented.accountId}: revoked or expired" }
    }

    return Rotation.Refused
  }

  private fun hash(rawToken: String): String {
    return SecureTokens.base64Url(SecureTokens.sha256(rawToken))
  }

  @Scheduled(fixedDelayString = "PT1H")
  @Transactional
  fun sweepExpired() {
    val removed = tokens.deleteExpired(LocalDateTime.now())
    if (removed > 0) {
      LOG.debug { "Swept $removed expired refresh tokens" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** Only has to be unique among live families, not unguessable - it never leaves the server. */
    private const val FAMILY_ID_BYTES = 16
  }
}
