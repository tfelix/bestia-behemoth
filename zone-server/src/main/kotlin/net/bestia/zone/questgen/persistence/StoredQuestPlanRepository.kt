package net.bestia.zone.questgen.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface StoredQuestPlanRepository : JpaRepository<StoredQuestPlan, Long> {

  fun findAllByState(state: StoredQuestPlan.State): List<StoredQuestPlan>

  fun findAllByStateIn(states: Collection<StoredQuestPlan.State>): List<StoredQuestPlan>

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from StoredQuestPlan p where p.id = :id")
  fun findLocked(id: Long): StoredQuestPlan?
}
