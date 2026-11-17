package net.bestia.zone.blackboard.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface BlackboardPostingRepository : JpaRepository<BlackboardPosting, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from BlackboardPosting p where p.questId = :questId")
  fun findLockedByQuestId(questId: Long): BlackboardPosting?

  fun countByPosterMasterIdAndSettledFalse(posterMasterId: Long): Long

  fun findAllBySettledFalseAndClosesAtBefore(at: Instant): List<BlackboardPosting>

  fun findAllBySettledFalseAndDeadlineBefore(at: Instant): List<BlackboardPosting>

  fun deleteAllByQuestIdIn(questIds: Collection<Long>)
}
