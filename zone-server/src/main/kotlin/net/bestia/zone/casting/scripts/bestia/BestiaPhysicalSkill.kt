package net.bestia.zone.casting.scripts.bestia

import java.util.Random
import kotlin.math.sqrt
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.attack.AttackStrategyFactory
import net.bestia.zone.battle.attack.ThreadLocalRandomSource
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.skill.persistence.SkillMechanics
import org.springframework.stereotype.Component

/**
 * The strike, power strike and shot archetypes: a default attack whose damage is scaled by the skill's
 * coefficient. It misses and crits like a swing, because the docs fit it against one. A dash or a leap first
 * brings the caster to its target.
 */
@Component
class BestiaPhysicalSkill(
  private val attackStrategies: AttackStrategyFactory,
  private val random: Random = ThreadLocalRandomSource,
) : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val battle = ctx.battle as? EntityBattleContext ?: return false

    return attackStrategies.getAttackStrategy(battle).isAttackPossible(battle)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val battle = ctx.battle as? EntityBattleContext ?: return null
    val target = ctx.targetEntityId ?: return null
    val mechanics = ctx.skill.mechanics

    val rushed = closeIn(ctx, mechanics)
    battle.damageVariables.attackPhysicalMod =
      ctx.attackCoefficient * (1f + mechanics.dashBonusPerTile * rushed) * weightFactor(ctx, mechanics)
    battle.damageVariables.ignoreDefense = mechanics.ignoreDefense
    battle.damageVariables.criticalChanceMod *= 1f + mechanics.critBonus / 100f

    val strategy = attackStrategies.getAttackStrategy(battle)
    val damage = SkillRiders.strike(mechanics, random) { strategy.execute(battle) }

    if (mechanics.leap > 0) landOn(ctx, mechanics)
    SkillRiders.afterHit(ctx, target, damage, random)

    return damage
  }

  /** Rushes or jumps up to the target, stopping next to it. Returns the tiles covered. */
  private fun closeIn(ctx: SkillContext, mechanics: SkillMechanics): Int {
    val reach = maxOf(mechanics.dash, mechanics.leap)
    if (reach <= 0) return 0
    val target = ctx.targetEntityId?.let { ctx.world.positionOf(it) } ?: return 0

    return ctx.world.forceMove(ctx.casterId, target, reach, keepDistance = 1)
  }

  /** A leap's statuses land on every foe around where it came down, not only on the one it aimed at. */
  private fun landOn(ctx: SkillContext, mechanics: SkillMechanics) {
    if (mechanics.landingRadius <= 0) return
    val landing = ctx.world.positionOf(ctx.casterId) ?: return

    ctx.world.entitiesInCube(landing, mechanics.landingRadius * 2L)
      .filter { it != ctx.targetEntityId && ctx.world.isFoe(it) }
      .forEach { SkillRiders.inflict(ctx, it) }
  }

  /**
   * A heavier body, or one weighed down by its load, slams harder: the square root of its weight against
   * [REFERENCE_KILOGRAMS], kept between half and three times the coefficient.
   */
  private fun weightFactor(ctx: SkillContext, mechanics: SkillMechanics): Float {
    if (!mechanics.weightScaling) return 1f

    val kilograms = (ctx.world.natureOf(ctx.casterId)?.weight ?: 0) + ctx.world.carriedKilogramsOf(ctx.casterId)

    return sqrt(kilograms / REFERENCE_KILOGRAMS).coerceIn(MIN_WEIGHT_FACTOR, MAX_WEIGHT_FACTOR)
  }

  private companion object {
    const val REFERENCE_KILOGRAMS = 60f
    const val MIN_WEIGHT_FACTOR = 0.5f
    const val MAX_WEIGHT_FACTOR = 3f
  }
}
