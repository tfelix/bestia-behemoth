package net.bestia.login.gamelogin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * The single-use code the browser hands to the game's loopback listener.
 *
 * Stored only as a digest: a leak of this table yields hashes of codes that stopped being
 * redeemable a minute after they were minted. [consumedAt] is set by a conditional update rather
 * than a read-then-write, so two simultaneous exchanges cannot both succeed.
 */
@Entity
@Table(
  name = "authorization_code",
  indexes = [
    Index(name = "idx_authorization_code_expires", columnList = "expires_at")
  ]
)
class AuthorizationCode(
  @Id
  @Column(name = "code_hash", length = 64)
  val codeHash: String,

  @Column(nullable = false, length = 64)
  val loginSessionIdHash: String,

  @Column(nullable = false)
  val accountId: Long,

  @Column(nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now(),

  @Column(nullable = false)
  val expiresAt: LocalDateTime
) {

  @Column(nullable = true)
  var consumedAt: LocalDateTime? = null
}
