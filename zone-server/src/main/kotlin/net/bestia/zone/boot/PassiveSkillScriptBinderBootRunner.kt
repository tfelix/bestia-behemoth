package net.bestia.zone.boot

import net.bestia.zone.battle.skill.passive.PassiveSkillScriptRegistry
import net.bestia.zone.skill.SkillRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Resolves every [net.bestia.zone.battle.skill.passive.PassiveSkillScript] bean to the skill it
 * names, once the skill import has run, so the per-recalc lookup in `StatusValueRecalcSystem` never
 * has to touch the database. The passive counterpart of [EquipmentScriptBinderBootRunner], and
 * ordered right after it for the same reason: it needs the catalogue its importer
 * ([SkillImporterBootRunner], order 101) has just written.
 *
 * ### Why a CommandLineRunner
 *
 * `WorldBootRunner` starts the tick loop from a runner, so a binder that ran any later would publish its
 * mapping after the world had already ticked, leaving the first recalcs (including entities dirtied by
 * [StatusEffectRestoreBootRunner], order 111) looking at an empty registry.
 */
@Component
@Order(151)
class PassiveSkillScriptBinderBootRunner(
  private val skillRepository: SkillRepository,
  private val passiveSkillScriptRegistry: PassiveSkillScriptRegistry,
) : CommandLineRunner {

  override fun run(vararg args: String?) {
    passiveSkillScriptRegistry.bind(skillRepository.findAll())
  }
}
