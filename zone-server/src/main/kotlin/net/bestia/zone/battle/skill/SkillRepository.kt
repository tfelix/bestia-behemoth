package net.bestia.zone.battle.skill

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull

interface SkillRepository : JpaRepository<Skill, Long> {

  fun findByIdentifier(identifier: String): Skill?
}

fun SkillRepository.findByIdOrThrow(id: Long): Skill {
  return findByIdOrNull(id) ?: throw SkillNotFoundException(id)
}
