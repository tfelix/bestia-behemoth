package net.bestia.zone.casting

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.util.EntityId

/**
 * The implementation of one catalogued skill, resolved from `skills.yml`'s `script` name by
 * [SkillStrategyFactory]. A skill with no [SkillStrategy] cannot be cast at all - which is exactly how
 * a passive, and a skill nobody has implemented yet, are told apart from an active one.
 *
 * A basic attack is deliberately **not** one of these: a sword swing needs no script, only the weapon
 * and the stats, and it goes through [net.bestia.zone.battle.attack.AttackStrategy] and [net.bestia.zone.battle.attack.AttackExecutionService] instead.
 *
 * ### What a script may do
 *
 * Everything it does to the world goes through [SkillContext.world], which charges a per-cast budget -
 * spawning an effect, querying what is standing nearby, placing a station, handing relational work to a
 * background worker. A script must **never** inject `World` or `WorldView`: it would reach the world
 * without being charged, and the budget is what keeps one cast from holding the tick.
 *
 * Implementations are singletons shared by every cast, so they must be stateless.
 */
interface SkillStrategy {

  /**
   * The status a toggle skill keeps on its caster, like Hide or Shield Wall: casting the skill while the status
   * is on drops it instead, for free. Null for every other skill.
   */
  val toggledStatus: StatusEffectId?
    get() = null

  /**
   * Whether resolving the cast counts as an action, which ends a hiding or a sprint on the caster. False for a skill
   * cast from hiding that leaves the caster hidden, like Shadow Step.
   */
  val isReportedAsAction: Boolean
    get() = true

  /** What casting [skill] at [level] costs and how far it reaches; the catalogue's single values by default. */
  fun costOf(skill: Skill, level: Int): SkillLevelCost = SkillLevelCost.of(skill)

  /**
   * How far the cast of [casterId] reaches: the [cost]'s range, unless the weapon in hand decides, as it does for a
   * bow skill. Asked on the tick as the cast resolves, before the range check; it may only read.
   */
  fun reachOf(world: World, casterId: EntityId, cost: SkillLevelCost): Long {
    return cost.rangeTiles
  }

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
   * Runs in the message handler inside the caster's own `modify` scope, which is why it is handed the [World]
   * rather than the budgeted [SkillWorld] a resolving cast gets: there is no budget yet and no snapshot to
   * take. The world is the handler's for the duration, so an implementation may read live components - but it may not
   * do relational work, and having nothing to write it has no reason to.
   *
   * [args] is the choice the player sent with the cast, if the skill asks for one. It is user input: a skill that
   * reads it refuses what it does not accept here, with `SKILL_INVALID_CHOICE`, and checks it again on resolution.
   *
   * @return the refusal to report to the player, or null when the cast may start
   */
  fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long> = emptyList()): OpError? = null

  /**
   * [checkCastStart] for a choice that only makes sense on the picked target, like the bestia Spell Training
   * teaches. Runs at activation once the target is known, under the same rules; null when the cast may start.
   */
  fun checkTarget(world: World, casterId: EntityId, targetEntityId: EntityId?, skillLevel: Int, args: List<Long>): OpError? = null

  /**
   * A choice that is no cast at all, like marking a spot for Warp Portal: done at once, with no cost, no cast bar
   * and no cooldown. Runs at activation in the caster's own scope, before anything else is checked.
   *
   * @return true when [args] asked for such a choice and it is done; false lets the cast go on as usual
   */
  fun freeAction(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): Boolean = false

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

  /** Acts again while the skill channels, see `SkillMechanics.channelSeconds`; [tick] counts from 0. */
  fun onChannelTick(ctx: SkillContext, tick: Int) {
  }
}
