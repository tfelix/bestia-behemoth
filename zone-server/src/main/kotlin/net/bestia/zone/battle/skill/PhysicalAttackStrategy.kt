package net.bestia.zone.battle.skill

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.BaseDamageCalculator
import net.bestia.zone.battle.damage.CriticalHit
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.util.clamp
import java.util.Random

/**
 * Resolves a physical basic attack: does it connect, is it a critical, and how much does it take off.
 *
 * Melee and ranged are the same class with a different [damageCalculator], because that is the only thing that
 * actually differs - which attribute the power comes from and how hard armour bites are both questions for the
 * calculator, and reach and line of sight are data on [BattleAttack] rather than properties of the strategy.
 */
class PhysicalAttackStrategy(
  private val damageCalculator: BaseDamageCalculator,
  private val losService: LineOfSightService,
  private val random: Random
) : AttackStrategy {

  /**
   * A physical attack needs something to hit, so an aimed-at point on the ground is refused rather than
   * resolved into a miss - there is nothing there to swing at.
   */
  override fun isAttackPossible(ctx: BattleContext): Boolean {
    if (ctx !is EntityBattleContext) {
      return false
    }

    if (!isAttackInRange(ctx)) {
      return false
    }

    return !ctx.usedAttack.needsLineOfSight ||
        losService.hasLineOfSight(ctx.attacker.position, ctx.targetPosition())
  }

  override fun execute(ctx: BattleContext): Damage {
    if (ctx !is EntityBattleContext) {
      return Miss
    }

    if (isMiss(ctx)) {
      return Miss
    }

    val critical = isCriticalHit(ctx)
    val amount = damageCalculator.calculateDamage(ctx, isCritical = critical)

    LOG.trace { "physical attack: ${if (critical) "critical " else ""}damage $amount" }

    return if (critical) CriticalHit(amount) else HitDamage(amount)
  }

  private fun isAttackInRange(ctx: BattleContext): Boolean =
    ctx.attacker.position.distance(ctx.targetPosition()) <= ctx.usedAttack.range

  /**
   * The roll is against *hitting*, and this answers the negation of it - a high hit rate has to mean mostly
   * landing.
   *
   * A ratio rather than Ragnarok Online's `80 + HIT - FLEE` difference, because the project's own HIT and FLEE
   * are not on RO's scale: they start at 175 and 100 (see
   * [net.bestia.zone.battle.status.DerivedStatusValues]), so the difference form would sit pinned at its cap
   * for every evenly-matched pair.
   *
   * **Known consequence, unresolved:** the ratio's two constants stop mattering as attributes grow, so two
   * evenly-matched fighters get *less* accurate as they level - about 83% at Lv.1, 79% at Lv.10, 69% at Lv.50
   * and 63% at Lv.100, tending to 50%. Whether an even fight should stay at a fixed accuracy is a balance
   * decision rather than a bug in this method, and fixing it means changing HIT and FLEE themselves.
   */
  private fun isMiss(ctx: EntityBattleContext): Boolean {
    val hitrate = (HITRATE_WEIGHT * ctx.attacker.derivedStatusValues.hitrate / ctx.defender.derivedStatusValues.flee)
      .clamp(MIN_HIT_CHANCE, MAX_HIT_CHANCE)

    LOG.trace { "Hit chance: $hitrate" }

    return random.nextFloat() >= hitrate
  }

  /**
   * Whether this swing crits, from the documented `CRIT` stat rather than from a formula of its own.
   *
   * The version this replaces summed three attacker/defender ratios and reached 0.95 - its own clamp ceiling -
   * for two entities with identical attributes, so essentially every hit was a critical. Since a critical also
   * bypasses defence ([BaseDamageCalculator]), that made armour nearly irrelevant.
   *
   * `CRIT` is `WIL/3`, read as a percentage the way RO reads LUK-derived crit, less a shield from the target's
   * own `CRIT` - RO shields with a fraction of the target's LUK for the same reason: a build that invested in
   * the stat should be harder to crit, not just better at critting.
   */
  private fun isCriticalHit(ctx: EntityBattleContext): Boolean {
    val chanceMod = ctx.damageVariables.criticalChanceMod

    // Zero means an effect said "this cannot crit", and the [MIN_CRIT_CHANCE] floor below must not overrule it -
    // the floor is there so a build with no willpower still crits occasionally, not to defeat a suppression.
    if (chanceMod <= 0f) {
      return false
    }

    val shield = ctx.defender.derivedStatusValues.crit / CRIT_SHIELD_DIVISOR
    val critPercent = (ctx.attacker.derivedStatusValues.crit - shield) * chanceMod
    val crit = (critPercent / 100f).clamp(MIN_CRIT_CHANCE, MAX_CRIT_CHANCE)

    LOG.trace { "Crit chance: $crit" }

    return random.nextFloat() < crit
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** Evenly-matched low-level attributes land about four swings in five; see [isMiss] on how that drifts. */
    private const val HITRATE_WEIGHT = 0.5f
    private const val MIN_HIT_CHANCE = 0.05f
    private const val MAX_HIT_CHANCE = 1f

    private const val CRIT_SHIELD_DIVISOR = 5f
    private const val MIN_CRIT_CHANCE = 0.01f

    /** Nothing crits reliably; there is always a swing that lands ordinarily. */
    private const val MAX_CRIT_CHANCE = 0.95f
  }
}
