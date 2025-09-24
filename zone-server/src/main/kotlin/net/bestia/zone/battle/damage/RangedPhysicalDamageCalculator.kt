package net.bestia.zone.battle.damage

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.EntityBattleContext
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max


/**
 * This calculates the raw damage of an attack if all other variables are known.
 * This calculates the damage of ranged weapons.
 *
 * @author Thomas Felix
 */
class RangedPhysicalDamageCalculator(
    random: Random = ThreadLocalRandom.current()
) : BaseDamageCalculator(random) {
  override fun getBonusAttack(battleCtx: EntityBattleContext): Float {
    return battleCtx.damageVariables.attackRangedBonus
  }

  // Same as MeleePhysicalDamageCalculator
  override fun getSoftDefense(battleCtx: EntityBattleContext): Float {
    /*
    val defStatus = battleCtx.defenderStatusPoints
    val lv = battleCtx.defenderLevel

    val softDef = lv / 2f + defStatus.vitality + defStatus.strength / 3f

    return min(0f, softDef)*/
    TODO("Not yet implemented")
  }

  /**
   * @return The attack value based solely on the status of the entity.
   */
  override fun getStatusAttack(battleCtx: EntityBattleContext): Float {
    /*
    val lvMod = battleCtx.attackerLevel / 4f
    val sp = battleCtx.attackerStatusPoints

    return lvMod + sp.dexterity + sp.strength / 5f*/
    TODO("Not yet implemented")
  }

  override fun getHardDefenseModifier(battleCtx: EntityBattleContext): Float {
    /*
    val physicalDefenseMod = battleCtx.damageVariables.physicalDefenseMod
    val defStatus = battleCtx.defenderStatusPoints
    val defDefense = battleCtx.defenderDefense.physicalDefense

    return (1 - (defDefense / 100f + physicalDefenseMod)).clamp(0.05f, 1.0f)*/
    TODO("Not yet implemented")
  }

  override fun getAttackModifier(battleCtx: EntityBattleContext): Float {
    val dmgVars = battleCtx.damageVariables

    return max(0f, dmgVars.attackMeleeMod)
  }

  override fun calculateDamage(battleCtx: EntityBattleContext): Int {
    TODO("Not yet implemented")
  }

  override fun calculateWeaponAtk(): Float {
    LOG.warn("calculateWeaponAtk is currently not implemented.")
    return 0f
  }

  override fun getAmmoAttack(battleCtx: EntityBattleContext): Float {
    // TODO Use proper ammo attack dmg when its implemented
    return 0f
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}