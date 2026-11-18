package net.bestia.zone.questgen.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface StoredQuestPlanRepository : JpaRepository<StoredQuestPlan, Long> {

  fun findAllByState(state: StoredQuestPlan.State): List<StoredQuestPlan>
}
