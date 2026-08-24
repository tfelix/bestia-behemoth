package net.bestia.login.recovery

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface RecoveryCodeRepository : JpaRepository<RecoveryCode, Long> {

  fun deleteAllByAccountId(accountId: Long)

  fun countByAccountIdAndUsedAtIsNull(accountId: Long): Long

  /**
   * Conditional update for the same reason as the authorization code: two concurrent redemptions
   * of one code must not both succeed.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
    """
    UPDATE RecoveryCode r
    SET r.usedAt = :now
    WHERE r.accountId = :accountId AND r.codeHash = :codeHash AND r.usedAt IS NULL
    """
  )
  fun redeem(
    @Param("accountId") accountId: Long,
    @Param("codeHash") codeHash: String,
    @Param("now") now: LocalDateTime
  ): Int
}
