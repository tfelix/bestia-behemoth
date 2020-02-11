package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.battle.AttackType
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.floor
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
class MagicDamageCalculator(
    private val rand: Random
) : DamageCalculator {

  /**
   * This calculates the taken battle damage. Currently this is only a
   * placeholder until the real damage formula is invented.
   *
   * Please not that this method ONLY calculates the damage. If the attack is
   * controlled by a script this wont get checked by this method anymore. Only
   * raw damage calculation is performed.
   */
  override fun calculateDamage(battleCtx: BattleContext): Int {
    val baseAtk = getBaseAttack(battleCtx)
    val atkMod = getAttackModifier(battleCtx)
    val hardDefMod = getHardDefenseModifier(battleCtx)
    val critMod = getCritModifier(battleCtx)
    val softDef = getSoftDefense(battleCtx)

    val damage = floor(baseAtk * atkMod * hardDefMod * critMod - softDef).toInt()

    LOG.trace("Damage (melee): {}.", damage)

    return damage
  }

  private fun getBaseAttack(battleCtx: BattleContext): Float {
    val usedAttack = battleCtx.usedAttack
    val (attackPhysicalBonus, attackMagicBonus) = battleCtx.damageVariables

    val statusAtk = getStatusAttack(battleCtx)
    val varMod = if (battleCtx.damageVariables.isCriticalHit) 1f else calculateVarMod()
    val weaponAtk = calculateWeaponAtk()
    val varModRed = varMod - varMod / 2 - 0.5f
    val ammoAtk = if (usedAttack.isRanged) getAmmoAtk(battleCtx) else 0f
    val bonusAtk = (if (usedAttack.isMagic) attackMagicBonus else attackPhysicalBonus).toInt()
    val elementMod = getElementMod(battleCtx)
    val elementBonusMod = getElementBonusMod(battleCtx)

    var baseAtk = 2f * statusAtk * varMod + weaponAtk * varModRed + ammoAtk + bonusAtk.toFloat()
    baseAtk *= elementMod * elementBonusMod
    baseAtk = min(1f, baseAtk)

    LOG.trace("BaseAtk: {}.", baseAtk)

    return baseAtk
  }

  /**
   * @return The attack value based solely on the status of the entity.
   */
  private fun getStatusAttack(battleCtx: BattleContext): Float {
    val lvMod = (battleCtx.attackerLevel / 4).toFloat()
    val sp = battleCtx.attackerStatusPoints

    return lvMod + sp.strength + sp.dexterity / 5f
  }

  private fun getSoftDefense(battleCtx: BattleContext): Float {
    val atk = battleCtx.usedAttack
    //Entity defender
    val defStatus = battleCtx.defenderStatusPoints
    val lv = battleCtx.defenderLevel

    var softDef: Float

    when (atk.type) {
      AttackType.MELEE_MAGIC, AttackType.RANGED_MAGIC -> {
        softDef = (lv / 2f + defStatus!!.vitality.toFloat() + defStatus.willpower / 4f
            + defStatus.intelligence / 5f)
        softDef = lv / 2f + defStatus.vitality.toFloat() + defStatus.strength / 3f
      }
      AttackType.MELEE_PHYSICAL, AttackType.RANGED_PHYSICAL -> {
        softDef = lv / 2f + defStatus!!.vitality.toFloat() + defStatus.strength / 3f
      }
      else -> softDef = 0f
    }

    softDef = Math.min(0f, softDef)

    LOG.trace("SoftDefense: {}.", softDef)

    return softDef
  }

  private fun getHardDefenseModifier(battleCtx: BattleContext): Float {
    val atk = battleCtx.usedAttack
    val physicalDefenseMod = battleCtx.damageVariables.physicalDefenseMod
    val magicDefenseMod = battleCtx.damageVariables.magicDefenseMod
    val defStatus = battleCtx.defenderStatusPoints!!

    val defMod = when (atk.type) {
      AttackType.MELEE_MAGIC, AttackType.RANGED_MAGIC -> 1 - (defStatus.magicDefense / 100f + magicDefenseMod)
      AttackType.MELEE_PHYSICAL, AttackType.RANGED_PHYSICAL -> 1 - (defStatus.physicalDefense / 100f + physicalDefenseMod)
      else -> 1f
    }.clamp(0.05f, 1.0f)

    LOG.trace("HardDefenseMod: {}.", defMod)

    return defMod
  }

  /**
   * @return Gets the attack modifier of the attack.
   */
  private fun getAttackModifier(battleCtx: BattleContext): Float {

    val atk = battleCtx.usedAttack
    val dmgVars = battleCtx.damageVariables

    var atkMod = when (atk.type) {
      AttackType.MELEE_MAGIC, AttackType.MELEE_PHYSICAL -> dmgVars.attackMagicMod * dmgVars.attackMeleeMod
      AttackType.RANGED_MAGIC, AttackType.RANGED_PHYSICAL -> dmgVars.attackRangedMod * dmgVars.attackRangedMod
      else -> 1.0f
    }.clamp(0f)

    LOG.trace("AttackMod: {}.", atkMod)

    return min(0f, atkMod)
  }

  /**
   * @return The critical modifier.
   */
  private fun getCritModifier(battleCtx: BattleContext): Float {
    val dmgVars = battleCtx.damageVariables

    val critMod = (if (dmgVars.isCriticalHit) 1.4f * dmgVars.criticalDamageMod else 1.0f).clamp(1.0f)
    LOG.trace("CritMod: {}.", critMod)

    return critMod
  }

  private fun getElementMod(battleCtx: BattleContext): Float {

    val atkEle = battleCtx.attackElement
    val defEle = battleCtx.defenderElement

    val eleMod = ElementModifier.getModifier(atkEle, defEle) / 100f
    LOG.trace("ElementMod: {}", eleMod)
    return eleMod
  }

  private fun getElementBonusMod(battleCtx: BattleContext): Float {
    val atkEle = battleCtx.attackElement
    val eleMod = battleCtx.damageVariables.getElementBonusMod(atkEle)
    LOG.trace("ElementBonusMod: {}", eleMod)
    return eleMod
  }

  private fun calculateWeaponAtk(): Float {
    LOG.warn("calculateWeaponAtk is currently not implemented.")

    val weaponAtk = 10f
    LOG.trace("WeaponAtk: {}", weaponAtk)
    return weaponAtk
  }

  private fun getAmmoAtk(battleCtx: BattleContext): Float {
    // TODO Auto-generated method stub
    return 0f
  }

  /**
   * Calculates the variance modification.
   *
   * @return A random value between 0.85 and 1.
   */
  private fun calculateVarMod(): Float {
    return 1 - rand.nextFloat() * 0.15f
  }
}