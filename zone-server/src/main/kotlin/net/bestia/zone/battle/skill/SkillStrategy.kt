package net.bestia.zone.battle.skill

import net.bestia.zone.battle.damage.Damage

/**
 * The implementation of one catalogued skill, resolved from `skills.yml`'s `script` name by
 * [SkillStrategyFactory]. A skill with no [SkillStrategy] cannot be cast at all - which is exactly how
 * a passive, and a skill nobody has implemented yet, are told apart from an active one.
 *
 * A basic attack is deliberately **not** one of these: a sword swing needs no script, only the weapon
 * and the stats, and it goes through [AttackStrategy] and [AttackExecutionService] instead.
 *
 * ### What a script may do
 *
 * Everything it does to the world goes through [SkillContext.world], which charges a per-cast budget -
 * spawning an effect, querying what is standing nearby, placing a station, handing relational work to a
 * background worker. A script must **never** inject `World` or `WorldView`: it is collected into
 * [SkillStrategyFactory], which `CastingSystem` transitively depends on, and the `World` bean is
 * assembled from every system - so injecting one closes a cycle Spring refuses to build and the whole
 * context fails at boot.
 *
 * Implementations are singletons and must be stateless; several casts resolve concurrently on different
 * workers.
 */
interface SkillStrategy {

  /**
   * Whether the cast may go ahead at all: range, line of sight, a target that is eligible, ammunition,
   * a reagent. Nothing is spent and nothing happens when this is false.
   *
   * Checked at *resolution* rather than activation, which is what makes a channelled skill fizzle when
   * the caster wandered out of range while casting. Whether the attack then *hits* is [execute]'s
   * business.
   */
  fun isCastPossible(ctx: SkillContext): Boolean

  /**
   * Resolves the skill, returning the one number to show on the target - or null when there is nothing
   * to show, which is the normal case for a skill whose whole effect is a patch of ground, a station, a
   * chart or a status effect.
   */
  fun execute(ctx: SkillContext): Damage?
}
