package net.bestia.zone.battle.attack

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.ElementModifier
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.ElementalSpell
import net.bestia.zone.battle.damage.Heal
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.MagicDamageCalculator
import net.bestia.zone.battle.damage.Miss

/**
 * A magic attack, RO pre-renewal style: it never misses and never crits, so FLEE, a wind ward and Pneuma do nothing
 * to it; MDEF is what stops a spell (see [ElementalSpell]), and size does not weigh on it.
 *
 * [execute] is the basic attack of a magic [AttackType], its power from MATK. [land] is for a skill script that
 * computed its power from its own documented formula.
 */
class MagicAttackStrategy(
  private val damageCalculator: MagicDamageCalculator,
  private val losService: LineOfSightService,
) : AttackStrategy {

  override fun isAttackPossible(ctx: BattleContext): Boolean {
    if (ctx !is EntityBattleContext) {
      return false
    }
    if (ctx.attacker.position.distance(ctx.targetPosition()) > ctx.usedAttack.range) {
      return false
    }

    return !ctx.usedAttack.needsLineOfSight || losService.hasLineOfSight(ctx.attacker.position, ctx.targetPosition())
  }

  override fun execute(ctx: BattleContext): Damage {
    if (ctx !is EntityBattleContext) {
      return Miss
    }

    val amount = damageCalculator.calculateDamage(ctx)
    if (ElementModifier.heals(ctx.usedAttack.attackElement, ctx.defender.assumedElement)) {
      return Heal(amount)
    }

    return HitDamage(amount)
  }

  /** A spell of [raw] power landing on [ctx]'s defender. */
  fun land(ctx: EntityBattleContext, raw: Float, ignoreDefense: Boolean = false): Damage {
    return ElementalSpell.land(raw, ctx, ignoreDefense)
  }
}
