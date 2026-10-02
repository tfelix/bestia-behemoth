package net.bestia.zone.bestia

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface BestiaSkillRepository : JpaRepository<BestiaSkill, Long> {

  fun findAllByBestiaId(bestiaId: Long): List<BestiaSkill>

  @Query("select new net.bestia.zone.bestia.LearnedSkill(s.bestia.id, s.skill.id, s.requiredLevel) from BestiaSkill s")
  fun findAllLearned(): List<LearnedSkill>
}

/** One learnset row as plain values, safe to keep after the session that loaded it is gone. */
data class LearnedSkill(val bestiaId: Long, val skillId: Long, val requiredLevel: Int)
