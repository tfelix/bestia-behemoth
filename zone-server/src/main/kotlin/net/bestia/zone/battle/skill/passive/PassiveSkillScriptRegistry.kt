package net.bestia.zone.battle.skill.passive

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.skill.SkillStrategyFactory
import net.bestia.zone.skill.Skill
import org.springframework.stereotype.Component

/**
 * Resolves each [PassiveSkillScript] bean to the skill id it implements - the passive counterpart of
 * [net.bestia.zone.item.equip.script.EquipmentScriptRegistry], and the same shape for the same
 * reason: `net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem` needs the mapping on the tick
 * thread, where a repository round trip is not acceptable.
 *
 * The mapping runs identifier -> id rather than the other way round, because a script names its own
 * skill (see [PassiveSkillScript.skill]); the id itself is content owned by `skills.yml`.
 * It is injected once at boot by
 * [net.bestia.zone.boot.PassiveSkillScriptBinderBootRunner], which must be a `CommandLineRunner`
 * rather than an `ApplicationReadyEvent` listener like the neighbouring *validators* - the tick loop
 * is already running by the time that event fires, so a late binding would leave the first recalcs
 * looking at an empty registry.
 */
@Component
class PassiveSkillScriptRegistry(
  scripts: List<PassiveSkillScript>,
  private val skillStrategyFactory: SkillStrategyFactory,
) {

  private val byName: Map<String, PassiveSkillScript> = scripts
    .mapNotNull { script -> script::class.simpleName?.let { it to script } }
    .toMap()

  @Volatile
  private var bySkillId: Map<Long, PassiveSkillScript> = emptyMap()

  /**
   * Resolves every script's [PassiveSkillScript.skill] against the catalogue.
   *
   * Throws rather than warning, unlike
   * [net.bestia.zone.battle.skill.scripts.SkillScriptBootValidator]: that one tolerates misses
   * because plenty of catalogued skills legitimately have no implementation yet, whereas this
   * direction - a script bean that exists in code - can only miss through a typo or a renamed
   * skill. The reverse direction is deliberately *not* checked: a passive skill with no script is
   * the normal case for most of the catalogue.
   */
  fun bind(skills: List<Skill>) {
    val byIdentifier = skills.associateBy { it.identifier }

    bySkillId = byName.values.associateBy { script ->
      val skill = byIdentifier[script.skill.name]
        ?: throw PassiveSkillScriptBindingException(
          "${script::class.simpleName} names unknown skill '${script.skill}'"
        )

      // A skill cannot be both cast and folded into the status recalc: the two ask different questions of
      // the same level, and nothing decides which one a point bought. With `Skill.type` gone this is the
      // check that catches it - a passive is a skill *without* a SkillStrategy, by definition.
      if (skillStrategyFactory.isCastable(skill)) {
        throw PassiveSkillScriptBindingException(
          "${script::class.simpleName} names skill '${script.skill}', which also has the " +
            "castable script '${skill.script}' - a skill is either cast or always-on, not both"
        )
      }

      skill.id
    }

    // A "bound to 0" here is the shipped-dead signal for the whole passive pipeline.
    LOG.info {
      "Registered ${byName.size} passive skill script(s): ${byName.keys.sorted()}, " +
        "bound to ${bySkillId.size} skill(s)"
    }
  }

  fun get(scriptName: String): PassiveSkillScript? = byName[scriptName]

  /**
   * Every bound script keyed by the skill id it implements.
   *
   * The recalc iterates this and asks [net.bestia.zone.ecs.battle.skill.KnownSkills] for each level,
   * rather than iterating an entity's known skills. That is the cheaper direction while scripted
   * passives number a handful and a master can know dozens of skills, and it spares `KnownSkills` an
   * iteration accessor it has no other use for. Invert it if scripted passives ever reach the same
   * order as skills known.
   */
  fun bound(): Map<Long, PassiveSkillScript> = bySkillId

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
