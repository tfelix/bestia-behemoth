package net.bestia.zone.boot

import net.bestia.zone.skill.SkillIds
import net.bestia.zone.skill.persistence.SkillRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Resolves every [net.bestia.zone.skill.SkillId] to its catalogue id once, right after
 * [SkillImporterBootRunner] (101) wrote the rows. A lazy lookup on first use would run on the tick, where `TickSqlGuard` forbids SQL.
 */
@Component
@Order(102)
class SkillIdsBootRunner(
  private val skillRepository: SkillRepository,
  private val skillIds: SkillIds,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    skillIds.load(skillRepository.findAll().associate { it.identifier to it.id })
  }
}
