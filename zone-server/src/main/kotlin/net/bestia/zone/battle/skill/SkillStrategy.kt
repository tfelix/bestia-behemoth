package net.bestia.zone.battle.skill

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

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
   * Whether the skill's cost can be met *now*, at activation - before the cast bar goes up. Nothing to
   * check by default.
   *
   * **Read-only.** It answers whether the reagent is there, and the reagent is spent when the cast lands,
   * not here: a cast that is interrupted, walked out of or cancelled must cost nothing. So every skill that
   * overrides this checks the same thing twice - here so the player is told at the button rather than after
   * channelling for five seconds, and again where it is actually taken, because the seconds in between are
   * long enough to drop, trade or use up what was counted. `CraftingService` checks its inputs at
   * `start` and again at `resolve` for exactly that reason.
   *
   * Separate from [isCastPossible] because it answers a different question at a different moment: that one
   * asks whether the cast still makes sense when it resolves, this one whether it should ever begin.
   *
   * Runs on the message thread inside the caster's own `modify` scope, which is why it is handed the [World]
   * rather than the budgeted [SkillWorld] a resolving cast gets: there is no budget yet and no snapshot to
   * take. The lock is held for the duration, so an implementation may read live components - but it may not
   * do relational work, and having nothing to write it has no reason to.
   *
   * @return the refusal to report to the player, or null when the cast may start
   */
  fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int): OpError? = null

  /**
   * Whether the cast may go ahead at all: range, line of sight, a target that is eligible. Nothing is
   * spent and nothing happens when this is false.
   *
   * Checked at *resolution* rather than activation, which is what makes a channelled skill fizzle when
   * the caster wandered out of range while casting. Whether the attack then *hits* is [execute]'s
   * business. Whether the cast can be *afforded* is neither of those - [checkCastStart] asks that before
   * the cast bar, and the script itself spends what it spends when it resolves.
   */
  fun isCastPossible(ctx: SkillContext): Boolean

  /**
   * Resolves the skill, returning the one number to show on the target - or null when there is nothing
   * to show, which is the normal case for a skill whose whole effect is a patch of ground, a station, a
   * chart or a status effect.
   */
  fun execute(ctx: SkillContext): Damage?
}
