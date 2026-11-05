package net.bestia.zone.postal.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface LetterRepository : JpaRepository<Letter, Long> {

  fun findByRecipientMasterIdAndExpiresAtAfterOrderBySentAtDesc(recipientMasterId: Long, now: Instant): List<Letter>

  fun countByRecipientMasterIdAndExpiresAtAfter(recipientMasterId: Long, now: Instant): Long

  fun findByIdAndRecipientMasterId(id: Long, recipientMasterId: Long): Letter?

  @Modifying
  @Query("delete from Letter l where l.expiresAt <= :now")
  fun deleteExpired(@Param("now") now: Instant): Int
}
