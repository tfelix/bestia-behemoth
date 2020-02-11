package net.bestia.zoneserver.battle

import mu.KotlinLogging
import kotlin.math.max
import kotlin.math.min

private val LOG = KotlinLogging.logger { }

/**
 * This calculates the raw damage of an attack if all other variables are known.
 * Even though no member state is saved this calculator is instanced as an
 * object in order to perform some kind of strategy pattern so the damage
 * calculation can be switched during runtime.
 *
 * @author Thomas Felix
 */
class MeleePhysicalDamageCalculator() : BaseDamageCalculator() {
  override fun calculateWeaponAtk(): Float {
    LOG.warn("calculateWeaponAtk is currently not implemented.")
    return 0f
  }

  override fun getBonusAttack(battleCtx: BattleContext): Float {
    // if (usedAttack.isMagic) attackMagicBonus else attackPhysicalBonus

    return battleCtx.damageVariables.attackMeleeBonus + battleCtx.damageVariables.attackPhysicalBonus
  }

  override fun getAmmoAttack(battleCtx: BattleContext): Float {
    return 0f
  }

  override fun getStatusAttack(battleCtx: BattleContext): Float {
    val lvMod = battleCtx.attackerLevel / 4f
    val sp = battleCtx.attackerStatusPoints

    return lvMod + sp.strength + sp.dexterity / 5f
  }

  // Same as RangedDamageCalculator
  override fun getSoftDefense(battleCtx: BattleContext): Float {
    val defStatus = battleCtx.defenderStatusPoints
    val lv = battleCtx.defenderLevel

    val softDef = lv / 2f + defStatus.vitality + defStatus.strength / 3f

    return min(0f, softDef)
  }

  override fun getHardDefenseModifier(battleCtx: BattleContext): Float {
    val physicalDefenseMod = battleCtx.damageVariables.physicalDefenseMod
    val defStatus = battleCtx.defenderStatusPoints

    return (1 - (defStatus.physicalDefense / 100f + physicalDefenseMod)).clamp(0.05f, 1.0f)
  }

  override fun getAttackModifier(battleCtx: BattleContext): Float {
    val dmgVars = battleCtx.damageVariables

    return max(0f, dmgVars.attackMeleeMod)
  }
}