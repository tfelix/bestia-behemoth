package net.bestia.zone.battle.attack

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LearnedSkillRepository : JpaRepository<LearnedSkill, Long> {

  fun findByPlayerBestiaIdAndSkillId(playerBestiaId: Long, skillId: Long): LearnedSkill?

  fun findAllByPlayerBestiaId(playerBestiaId: Long): List<LearnedSkill>

  fun findAllByMasterId(masterId: Long): List<LearnedSkill>

  fun findByMasterIdAndSkillId(masterId: Long, skillId: Long): LearnedSkill?
}
