package net.bestia.zone.battle.damage

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.EntityBattleContext
import java.util.*
import kotlin.math.max


/**
 * This calculates the raw damage of an attack if all other variables are known.
 * Even though no member state is saved this calculator is instanced as an
 * object in order to perform some kind of strategy pattern so the damage
 * calculation can be switched during runtime.
 *
 * @author Thomas Felix
 */
class MeleePhysicalDamageCalculator(
    random: Random
) : BaseDamageCalculator(random) {
  override fun calculateDamage(battleCtx: EntityBattleContext): Int {
    TODO("Not yet implemented")
  }

  override fun calculateWeaponAtk(): Float {
    LOG.warn { "calculateWeaponAtk is currently not implemented." }
    return 0f
  }

  override fun getBonusAttack(battleCtx: EntityBattleContext): Float {
    return battleCtx.damageVariables.attackMeleeBonus + battleCtx.damageVariables.attackPhysicalBonus
  }

  override fun getAmmoAttack(battleCtx: EntityBattleContext): Float {
    return 0f
  }

  override fun getStatusAttack(battleCtx: EntityBattleContext): Float {
    /*
    val lvMod = battleCtx.attackerLevel / 4f
    val sp = battleCtx.attackerStatusPoints

    return lvMod + sp.strength + sp.dexterity / 5f*/
    TODO("Not yet implemented")
  }

  // Same as RangedDamageCalculator
  override fun getSoftDefense(battleCtx: EntityBattleContext): Float {
    /*
    val defStatus = battleCtx.defenderStatusPoints
    val lv = battleCtx.defenderLevel

    val softDef = lv / 2f + defStatus.vitality / 2f + defStatus.strength / 4f

    return max(0f, softDef)*/
    TODO("Not yet implemented")
  }

  override fun getHardDefenseModifier(battleCtx: EntityBattleContext): Float {
    /*
    val physicalDefenseMod = battleCtx.damageVariables.physicalDefenseMod
    val defDefense = battleCtx.defenderDefense.physicalDefense

    return (1 - ((defDefense / 100f) * physicalDefenseMod)).clamp(0.05f, 1.0f)
     */
    TODO("Not yet implemented")
  }

  override fun getAttackModifier(battleCtx: EntityBattleContext): Float {
    val dmgVars = battleCtx.damageVariables

    return max(0f, dmgVars.attackMeleeMod)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}