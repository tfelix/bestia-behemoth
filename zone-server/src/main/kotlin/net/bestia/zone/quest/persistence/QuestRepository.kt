package net.bestia.zone.quest.persistence

import jakarta.persistence.LockModeType
import net.bestia.zone.quest.QuestStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface QuestRepository : JpaRepository<Quest, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from Quest q where q.id = :id")
  fun findLocked(id: Long): Quest?

  fun findAllByPosterMasterId(posterMasterId: Long): List<Quest>

  fun findAllByStatusInAndEndedAtBefore(statuses: Collection<QuestStatus>, endedBefore: Instant): List<Quest>
}
