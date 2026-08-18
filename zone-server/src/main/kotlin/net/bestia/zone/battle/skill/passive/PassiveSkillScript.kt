package net.bestia.zone.battle.skill.passive

import net.bestia.zone.battle.status.StatusValueRecalcContext

/**
 * The always-on effect of a [net.bestia.zone.battle.skill.SkillType.PASSIVE] skill: a contribution
 * folded into an entity's effective status values every time
 * `net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem` rebuilds them, scaled by how many
 * levels the entity has invested.
 *
 * Registered under its simple class name in [PassiveSkillScriptRegistry] and bound to a skill at
 * boot. Implementations must be stateless and must only mutate [StatusValueRecalcContext] - the
 * recalc runs on the tick thread over every dirty entity. A passive with no stat effect (most of the
 * catalogue today) simply has no script at all.
 *
 * ### Why the skill is named here rather than in `skills.yml`
 *
 * The obvious design is to reuse the existing nullable `script` column the way equipment does. That
 * would work now that `SkillImporterBootRunner.tryUpdate` propagates content edits onto existing
 * rows, but it buys nothing: the `script` column already means "the [net.bestia.zone.battle.skill.SkillStrategy] that resolves
 * this skill when cast", and a passive is never cast - [net.bestia.zone.battle.skill.SkillStrategyFactory] throws on a PASSIVE
 * skill and `SkillScriptBootValidator` skips them entirely. One column resolving into two unrelated
 * bean registries depending on the row's `type` is a worse contract than a name on the bean.
 *
 * Declaring the identifier on the bean keeps the two vocabularies apart, and follows what this
 * codebase already does for the two passives that were wired by hand -
 * [net.bestia.zone.environment.weather.EnvironmentalExposureSystem] and
 * [net.bestia.zone.environment.weather.WeatherPublisher] both resolve their skill by identifier,
 * "because the id in `skills.yml` is content and this is code". The same reasoning applies to the
 * script name.
 */
interface PassiveSkillScript {

  /**
   * The `skills.yml` identifier of the PASSIVE skill this implements, e.g. `INNER_PEACE`. Resolved
   * to a skill id once at boot; a value that matches no skill, or one that is not PASSIVE, fails
   * startup rather than going quietly inert.
   */
  val skillIdentifier: String

  /** Mutates [context] to reflect this passive being known at [level], which is always >= 1. */
  fun apply(context: StatusValueRecalcContext, level: Int)
}
