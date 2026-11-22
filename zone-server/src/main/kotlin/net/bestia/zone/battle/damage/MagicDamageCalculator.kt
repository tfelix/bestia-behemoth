package net.bestia.zone.battle.damage

import net.bestia.zone.battle.EntityBattleContext
import java.util.Random
import kotlin.math.max

/**
 * Magic damage: the same [BaseDamageCalculator] shape as the two physical calculators, off `MATK` and
 * `SoftMDEF` instead of `ATK` and `SoftDEF`.
 *
 * The basic attack of a magic [net.bestia.zone.battle.attack.AttackType], which
 * [net.bestia.zone.battle.attack.MagicAttackStrategy] resolves when nothing but the caster's MATK sets the power. A
 * skill script with a formula of its own lands through [ElementalSpell] instead, which weighs element and MDEF the
 * same way.
 */
class MagicDamageCalculator(
  random: Random
) : BaseDamageCalculator(random) {

  /** `MATK = BaseLv/4 + INT + WIL/5`. */
  override fun getStatusAttack(battleCtx: EntityBattleContext): Float =
    battleCtx.attacker.derivedStatusValues.matk.toFloat()

  override fun getBonusAttack(battleCtx: EntityBattleContext): Float =
    battleCtx.damageVariables.attackMagicBonus

  /** A spell does not care how big its target is; the size table is a weapon's. */
  override fun sizeFactor(battleCtx: EntityBattleContext): Float {
    return 1f
  }

  /** A spell carries no ammunition. */
  override fun getAmmoAttack(battleCtx: EntityBattleContext): Float = 0f

  /**
   * The documented soft MDEF (see [net.bestia.zone.battle.status.DefenseValues]), and nothing more: the same term
   * [ElementalSpell] subtracts, so a spell lands alike whichever way it was cast.
   */
  override fun getSoftDefense(battleCtx: EntityBattleContext): Float {
    return max(0f, battleCtx.defender.defense.magicDefense.toFloat())
  }

  /** Worn MDEF, times whatever [DamageVariables.magicDefenseMod] a script or effect has set. */
  override fun getHardDefenseModifier(battleCtx: EntityBattleContext): Float =
    magicDefenseModifier(battleCtx) * hardDefenseFactor(battleCtx.defender.defense.hardMagicDefense)

  override fun getAttackModifier(battleCtx: EntityBattleContext): Float =
    max(0f, battleCtx.damageVariables.attackMagicMod)

  /** A spell's power is its own, plus whatever the staff in hand contributes. */
  override fun calculateWeaponAtk(battleCtx: EntityBattleContext): Float =
    battleCtx.weapon.matk.toFloat()
}
