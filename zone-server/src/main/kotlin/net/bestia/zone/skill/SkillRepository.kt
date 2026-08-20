package net.bestia.zone.skill

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull

interface SkillRepository : JpaRepository<Skill, Long> {

  fun findByIdentifier(identifier: String): Skill?
}

fun SkillRepository.findByIdOrThrow(id: Long): Skill {
  return findByIdOrNull(id) ?: throw SkillNotFoundException(id)
}

/**
 * The catalogue row for a skill that code names.
 *
 * Nullable even though [SkillCatalogBootValidator] fails the boot on a constant the catalogue has no
 * row for: the fallbacks at the call sites are a second line rather than the live path, and they stay
 * because a content error should not take a subsystem down mid-session.
 */
fun SkillRepository.findByIdentifier(skillId: SkillId): Skill? {
  return findByIdentifier(skillId.name)
}
