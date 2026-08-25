package net.bestia.login.gamelogin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * One device's standing permission to obtain zone tokens without repeating the passkey ceremony.
 *
 * Opaque and stored as a digest rather than signed like the zone token. Signing would let the token
 * carry its own authority, and a token that carries its own authority cannot be taken away before
 * it expires - being able to take it away is the entire reason this table exists. Every use is a
 * lookup here, so revocation is immediate.
 *
 * Rotation is what makes theft visible. Redeeming a token consumes its row and issues a successor
 * in the same [familyId], so a token presented twice means a copy is in circulation: the second
 * presentation fails and the whole family goes with it.
 */
@Entity
@Table(
  name = "refresh_token",
  indexes = [
    Index(name = "idx_refresh_token_account", columnList = "account_id"),
    Index(name = "idx_refresh_token_family", columnList = "family_id"),
    Index(name = "idx_refresh_token_expires", columnList = "expires_at")
  ]
)
class RefreshToken(
  /** Base64url SHA-256 of the token the client holds. */
  @Id
  @Column(name = "token_hash", length = 64)
  val tokenHash: String,

  @Column(name = "account_id", nullable = false)
  val accountId: Long,

  /**
   * Shared by a token and every successor rotation makes from it, i.e. one unbroken chain from one
   * passkey login. Revoking a family logs that one device out and leaves the player's others alone,
   * which is why the family and not the account is the unit of revocation on replay.
   */
  @Column(name = "family_id", nullable = false, length = 64)
  val familyId: String,

  @Column(nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now(),

  @Column(nullable = false)
  val expiresAt: LocalDateTime
) {

  /** Set when the token is rotated. A consumed token presented again is evidence of a copy. */
  @Column(nullable = true)
  var consumedAt: LocalDateTime? = null

  @Column(nullable = true)
  var revokedAt: LocalDateTime? = null
}
