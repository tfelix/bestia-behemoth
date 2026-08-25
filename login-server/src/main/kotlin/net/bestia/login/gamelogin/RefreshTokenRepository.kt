package net.bestia.login.gamelogin

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface RefreshTokenRepository : JpaRepository<RefreshToken, String> {

  /**
   * Claims the token, returning 1 only for the caller that won it. Conditional for the same reason
   * [AuthorizationCodeRepository.consume] is: two clients refreshing at once must not both come away
   * with a successor, because the loser's copy would then look like a replay forever after.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
    """
    UPDATE RefreshToken t
    SET t.consumedAt = :now
    WHERE t.tokenHash = :tokenHash
      AND t.consumedAt IS NULL
      AND t.revokedAt IS NULL
      AND t.expiresAt > :now
    """
  )
  fun consume(@Param("tokenHash") tokenHash: String, @Param("now") now: LocalDateTime): Int

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
    """
    UPDATE RefreshToken t
    SET t.revokedAt = :now
    WHERE t.familyId = :familyId AND t.revokedAt IS NULL
    """
  )
  fun revokeFamily(@Param("familyId") familyId: String, @Param("now") now: LocalDateTime): Int

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
    """
    UPDATE RefreshToken t
    SET t.revokedAt = :now
    WHERE t.accountId = :accountId AND t.revokedAt IS NULL
    """
  )
  fun revokeAllForAccount(@Param("accountId") accountId: Long, @Param("now") now: LocalDateTime): Int

  fun countByAccountIdAndRevokedAtIsNullAndConsumedAtIsNull(accountId: Long): Long

  /**
   * Consumed and revoked rows keep their original expiry, so they are swept by the same cutoff as
   * the ones that simply ran out. Nothing needs them once they can no longer be presented.
   */
  @Modifying
  @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
  fun deleteExpired(@Param("cutoff") cutoff: LocalDateTime): Int
}
