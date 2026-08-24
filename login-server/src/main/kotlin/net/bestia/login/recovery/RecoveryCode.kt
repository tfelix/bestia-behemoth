package net.bestia.login.recovery

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * One single-use way back into an account whose passkeys are all gone.
 *
 * There is no email on file, so this is the entire recovery story: if the codes are lost as well,
 * the account cannot be recovered by anyone, and the page that hands them out says so before it
 * shows them.
 */
@Entity
@Table(
  name = "recovery_code",
  indexes = [
    Index(name = "idx_recovery_code_account", columnList = "account_id")
  ]
)
class RecoveryCode(
  @Column(name = "account_id", nullable = false)
  val accountId: Long,

  /**
   * Base64url SHA-256 of the normalized code. A plain digest rather than a password hash on
   * purpose: the code is 120 bits of CSPRNG output, so there is no dictionary for a work factor to
   * slow down.
   */
  @Column(nullable = false, length = 64)
  val codeHash: String,

  @Column(nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now()
) {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long = 0

  @Column(nullable = true)
  var usedAt: LocalDateTime? = null
}
