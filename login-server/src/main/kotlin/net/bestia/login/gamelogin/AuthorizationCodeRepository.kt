package net.bestia.login.gamelogin

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface AuthorizationCodeRepository : JpaRepository<AuthorizationCode, String> {

  /**
   * Claims the code, returning 1 only for the caller that actually won it. Expressed as a
   * conditional update because a select-then-update would let two concurrent exchanges both observe
   * an unconsumed row and both mint a token.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
    """
    UPDATE AuthorizationCode c
    SET c.consumedAt = :now
    WHERE c.codeHash = :codeHash AND c.consumedAt IS NULL AND c.expiresAt > :now
    """
  )
  fun consume(@Param("codeHash") codeHash: String, @Param("now") now: LocalDateTime): Int

  @Modifying
  @Query("DELETE FROM AuthorizationCode c WHERE c.expiresAt < :cutoff")
  fun deleteExpired(@Param("cutoff") cutoff: LocalDateTime): Int
}
