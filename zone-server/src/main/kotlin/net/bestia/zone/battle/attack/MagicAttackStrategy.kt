package net.bestia.zone.battle.attack

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.ElementalSpell
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.damage.MagicDamageCalculator
import net.bestia.zone.battle.damage.Miss

/**
 * Resolves a magic attack, RO pre-renewal style: it **never misses and never crits**. FLEE, a wind ward and a
 * Pneuma do nothing against it; MDEF is what stops a spell, worn as a percentage and attribute-derived as a flat
 * cut (see [ElementalSpell]). Size does not weigh on it either - the size table is a weapon's.
 *
 * Two ways in. [execute] is the basic attack of a magic [AttackType], its power from [MagicDamageCalculator]
 * (MATK). [land] is for a skill script that computed its own power from its documented formula - a Firebolt's
 * `(lv/4 + INT) * skillLv`, a bestia bolt's archetype - and wants it to land the way every spell does.
 */
class MagicAttackStrategy(
  private val damageCalculator: MagicDamageCalculator,
  private val losService: LineOfSightService,
) : AttackStrategy {

  /** A spell aimed at nothing has nothing to hurt; range and sight as for any attack. */
  override fun isAttackPossible(ctx: BattleContext): Boolean {
    if (ctx !is EntityBattleContext) return false
    if (ctx.attacker.position.distance(ctx.targetPosition()) > ctx.usedAttack.range) return false

    return !ctx.usedAttack.needsLineOfSight || losService.hasLineOfSight(ctx.attacker.position, ctx.targetPosition())
  }

  override fun execute(ctx: BattleContext): Damage {
    if (ctx !is EntityBattleContext) return Miss

    return HitDamage(damageCalculator.calculateDamage(ctx))
  }

  /** A spell of [raw] power landing on [ctx]'s defender. */
  fun land(ctx: EntityBattleContext, raw: Float, ignoreDefense: Boolean = false): Damage =
    HitDamage(ElementalSpell.damage(raw, ctx, ignoreDefense))
}
