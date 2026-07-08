package net.bestia.zone.battle.attack

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LearnedSkillRepository : JpaRepository<LearnedSkill, Long> {

  fun findAllByPlayerBestiaId(playerBestiaId: Long): List<LearnedSkill>

  fun findByPlayerBestiaIdAndSkillId(playerBestiaId: Long, skillId: Long): LearnedSkill?
}
