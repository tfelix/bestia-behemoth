package net.bestia.zone.battle.damage

import net.bestia.zone.battle.EntityBattleContext
import java.util.Random
import kotlin.math.max

/**
 * Physical damage for an arrow, a bolt or a sling stone: the DEX half of the pair with
 * [MeleePhysicalDamageCalculator].
 *
 * Its soft defence is deliberately *harsher* than melee's, which is RO's arrangement and the reason a ranged
 * attacker is not simply a melee attacker who is safe: `VIT` counts fully rather than as itself, so armour tells
 * more against arrows than against a sword.
 */
class RangedPhysicalDamageCalculator(
  random: Random
) : BaseDamageCalculator(random) {

  /** The ranged ATK: the melee term with STR and DEX swapped. */
  override fun getStatusAttack(battleCtx: EntityBattleContext): Float =
    battleCtx.attacker.derivedStatusValues.rangedAtk.toFloat()

  override fun getBonusAttack(battleCtx: EntityBattleContext): Float {
    val vars = battleCtx.damageVariables

    return vars.attackRangedBonus + vars.attackPhysicalBonus
  }

  /**
   * Zero until ammunition is a thing one can carry. `RangedPhysicalAttackStrategy` does not check for it either,
   * so an archer currently shoots for free - the arrow, when it exists, belongs in both places at once.
   */
  override fun getAmmoAttack(battleCtx: EntityBattleContext): Float = 0f

  /**
   * `SoftDEF + VIT/2 + STR/6`: the shared attribute defence, plus the extra that armour gives against something
   * thrown rather than swung.
   *
   * The addition is on top of [net.bestia.zone.battle.status.DefenseValues] rather than a second formula
   * replacing it, so the documented SoftDEF stays the one definition and this reads as what it is - a ranged
   * surcharge.
   */
  override fun getSoftDefense(battleCtx: EntityBattleContext): Float {
    val defender = battleCtx.defender
    val surcharge = defender.statusValues.vitality / 2f + defender.statusValues.strength / 6f

    return max(0f, defender.defense.defense + surcharge)
  }

  override fun getHardDefenseModifier(battleCtx: EntityBattleContext): Float = physicalDefenseModifier(battleCtx)

  override fun getAttackModifier(battleCtx: EntityBattleContext): Float {
    val vars = battleCtx.damageVariables

    return max(0f, vars.attackRangedMod) * max(0f, vars.attackPhysicalMod)
  }
}
