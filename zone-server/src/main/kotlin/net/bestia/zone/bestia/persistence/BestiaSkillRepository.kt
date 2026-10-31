package net.bestia.zone.bestia.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface BestiaSkillRepository : JpaRepository<BestiaSkill, Long> {

  fun findAllByBestiaId(bestiaId: Long): List<BestiaSkill>

  @Query("select new net.bestia.zone.bestia.persistence.LearnedSkill(s.bestia.id, s.skill.id, s.requiredLevel, s.active, s.skill.range) from BestiaSkill s")
  fun findAllLearned(): List<LearnedSkill>
}

/** One learnset row as plain values, safe to keep after the session that loaded it is gone. */
/** [active] and [range] are what the wild AI needs to fight with the skill, see [BestiaSkill.active]. */
data class LearnedSkill(
  val bestiaId: Long,
  val skillId: Long,
  val requiredLevel: Int,
  val active: Boolean = false,
  val range: Int? = null,
)
