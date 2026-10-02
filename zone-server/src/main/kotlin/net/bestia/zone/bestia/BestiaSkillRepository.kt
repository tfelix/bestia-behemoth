package net.bestia.zone.bestia

import org.springframework.data.jpa.repository.JpaRepository

interface BestiaSkillRepository : JpaRepository<BestiaSkill, Long> {

  fun findAllByBestiaId(bestiaId: Long): List<BestiaSkill>
}
