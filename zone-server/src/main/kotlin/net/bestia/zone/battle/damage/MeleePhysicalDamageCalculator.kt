package net.bestia.zone.battle.damage

import net.bestia.zone.battle.EntityBattleContext
import java.util.Random
import kotlin.math.max

/**
 * Physical damage for something swung, thrust or bitten: the STR half of the pair with
 * [RangedPhysicalDamageCalculator].
 *
 * Nothing about the *shape* of the formula differs between the two - see [BaseDamageCalculator]. What differs is
 * which attribute the attacker's power comes from and which of the [DamageVariables] bonuses apply.
 */
class MeleePhysicalDamageCalculator(
  random: Random
) : BaseDamageCalculator(random) {

  /** The documented melee ATK: `BaseLv/4 + STR + DEX/5 + WIL/3`. */
  override fun getStatusAttack(battleCtx: EntityBattleContext): Float =
    battleCtx.attacker.derivedStatusValues.atk.toFloat()

  override fun getBonusAttack(battleCtx: EntityBattleContext): Float {
    val vars = battleCtx.damageVariables

    return vars.attackMeleeBonus + vars.attackPhysicalBonus
  }

  /** A fist carries no ammunition, and never will. */
  override fun getAmmoAttack(battleCtx: EntityBattleContext): Float = 0f

  /**
   * `SoftDEF = VIT + STR/5 + AGI/5 + BaseLv/4`, straight off the target - see
   * [net.bestia.zone.battle.status.DefenseValues], which is where that formula lives and is the only place it
   * should. Subtracted flat, so it matters most against a weak attacker and fades against a strong one.
   */
  override fun getSoftDefense(battleCtx: EntityBattleContext): Float =
    max(0f, battleCtx.defender.defense.defense.toFloat())

  override fun getHardDefenseModifier(battleCtx: EntityBattleContext): Float = physicalDefenseModifier(battleCtx)

  override fun getAttackModifier(battleCtx: EntityBattleContext): Float {
    val vars = battleCtx.damageVariables

    return max(0f, vars.attackMeleeMod) * max(0f, vars.attackPhysicalMod)
  }
}
