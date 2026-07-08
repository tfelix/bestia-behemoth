package net.bestia.zone.battle.attack

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MasterLearnedSkillRepository : JpaRepository<MasterLearnedSkill, Long> {

  fun findAllByMasterId(masterId: Long): List<MasterLearnedSkill>

  fun findByMasterIdAndSkillId(masterId: Long, skillId: Long): MasterLearnedSkill?
}
