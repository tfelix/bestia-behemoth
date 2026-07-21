package net.bestia.zone.battle.status

import net.bestia.zone.util.EntityId

/**
 * The gameplay logic behind one [StatusEffectDefinition] - what it does to an entity's status
 * values, how long it lasts, and how re-application behaves. Registered under its simple class
 * name (see [StatusEffectScriptRegistry]) and referenced by [StatusEffectDefinition.script],
 * exactly the same pattern as [net.bestia.zone.battle.skill.SkillStrategy] /
 * [net.bestia.zone.battle.skill.SkillScriptRegistry] for skills.
 *
 * Every definition needs one, even a purely bookkeeping effect with nothing to apply - it still
 * needs to answer "how long" and "what happens on re-application", mirroring rAthena where every
 * `SC_` type has a duration/stacking entry even if it does nothing else.
 */
interface StatusEffectScript {
  val stackBehavior: StackBehavior
    get() = StackBehavior.REFRESH_DURATION

  fun durationSeconds(level: Int): Double

  /** Mutates [context] to reflect this effect being active at [level], sourced from [sourceEntityId]. */
  fun apply(context: StatusValueRecalcContext, level: Int, sourceEntityId: EntityId?) {
    // Bookkeeping-only effects (e.g. a resisted-once marker) have nothing to apply.
  }
}
