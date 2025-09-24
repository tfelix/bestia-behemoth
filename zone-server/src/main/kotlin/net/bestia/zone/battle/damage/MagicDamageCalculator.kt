package net.bestia.zone.battle.damage

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.EntityBattleContext
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max

/**
 * This calculates the raw damage of an attack if all other variables are known.
 * Even though no member state is saved this calculator is instanced as an
 * object in order to perform some kind of strategy pattern so the damage
 * calculation can be switched during runtime.
 *
 * @author Thomas Felix
 */
class MagicDamageCalculator(
    random: Random = ThreadLocalRandom.current()
) : BaseDamageCalculator(random) {
  override fun getBonusAttack(battleCtx: EntityBattleContext): Float {
    return battleCtx.damageVariables.attackMagicBonus
  }

  /**
   * @return The attack value based solely on the status of the entity.
   */
  override fun getStatusAttack(battleCtx: EntityBattleContext): Float {
    /*
    val lvMod = battleCtx.attackerLevel / 4f
    val sp = battleCtx.attackerStatusPoints

    return lvMod + sp.intelligence + sp.willpower / 5f*/
    TODO("Not yet implemented")
  }

  override fun getSoftDefense(battleCtx: EntityBattleContext): Float {
    /*
    val defStatus = battleCtx.defenderStatusPoints
    val lv = battleCtx.defenderLevel

    val softDef = lv / 2f + defStatus.vitality +
        defStatus.willpower / 4f +
        defStatus.intelligence / 5f

    return min(0f, softDef)*/

    TODO("Not yet implemented")
  }

  override fun getHardDefenseModifier(battleCtx: EntityBattleContext): Float {
    /*
    val magicDefenseMod = battleCtx.damageVariables.magicDefenseMod
    val defDefense = battleCtx.defenderDefense.magicDefense

    return (1 - (defDefense / 100f + magicDefenseMod)).clamp(0.05f, 1.0f)*/
    TODO("Not yet implemented")
  }

  override fun getAttackModifier(battleCtx: EntityBattleContext): Float {
    val dmgVars = battleCtx.damageVariables

    return max(0f, dmgVars.attackMagicMod)
  }

  override fun calculateDamage(battleCtx: EntityBattleContext): Int {
    TODO("Not yet implemented")
  }

  override fun calculateWeaponAtk(): Float {
    LOG.warn("calculateWeaponAtk is currently not implemented.")
    return 0f
  }

  // Magic does not use ammo
  override fun getAmmoAttack(battleCtx: EntityBattleContext): Float {
    return 0f
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}