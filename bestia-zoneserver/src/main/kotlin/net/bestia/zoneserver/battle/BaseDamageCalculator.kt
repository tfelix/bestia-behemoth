package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.zoneserver.battle.damage.DamageCalculator
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.floor
import kotlin.math.min

private val LOG = KotlinLogging.logger { }

abstract class BaseDamageCalculator : DamageCalculator {
  private val random = ThreadLocalRandom.current()

  /**
   * This calculates the taken battle damage. Currently this is only a
   * placeholder until the real damage formula is invented.
   *
   * Please not that this method ONLY calculates the damage. If the attack is
   * controlled by a script this wont get checked by this method anymore. Only
   * raw damage calculation is performed.
   */
  override fun calculateDamage(battleCtx: EntityBattleContext): Int {
    val baseAtk = getBaseAttack(battleCtx)
    val atkMod = getAttackModifier(battleCtx)
    val hardDefMod = getHardDefenseModifier(battleCtx)
    val critMod = getCritModifier(battleCtx)
    val softDef = getSoftDefense(battleCtx)

    val damage = floor(baseAtk * atkMod * hardDefMod * critMod - softDef).toInt()

    LOG.trace("Damage (melee): {}.", damage)

    return damage
  }

  private fun getBaseAttack(battleCtx: EntityBattleContext): Float {
    val statusAtk = getStatusAttack(battleCtx)
    val varMod = if (battleCtx.damageVariables.isCriticalHit) 1f else calculateVarMod()
    val weaponAtk = calculateWeaponAtk()
    val varModRed = calculateVarMod(0.05f)
    val ammoAtk = getAmmoAttack(battleCtx)
    val bonusAtk = getBonusAttack(battleCtx)
    val elementMod = getElementMod(battleCtx)
    val elementBonusMod = getElementBonusMod(battleCtx)

    var baseAtk = 2f * statusAtk * varMod + weaponAtk * varModRed + ammoAtk + bonusAtk
    baseAtk *= elementMod * elementBonusMod
    baseAtk = min(1f, baseAtk)

    LOG.trace("BaseAtk: {}.", baseAtk)

    return baseAtk
  }

  private fun getElementMod(battleCtx: EntityBattleContext): Float {
    val atkEle = battleCtx.attackElement
    val defEle = battleCtx.defenderElement

    val eleMod = ElementModifier.getModifier(atkEle, defEle) / 100f
    LOG.trace("ElementMod: {}", eleMod)
    return eleMod
  }

  private fun getElementBonusMod(battleCtx: EntityBattleContext): Float {
    val atkEle = battleCtx.attackElement
    val defEle = battleCtx.defenderElement

    return ElementModifier.getModifierFloat(atkEle, defEle)
  }

  /**
   * @return The critical modifier.
   */
  private fun getCritModifier(battleCtx: EntityBattleContext): Float {
    val dmgVars = battleCtx.damageVariables

    val critMod = (if (dmgVars.isCriticalHit) 1.4f * dmgVars.criticalDamageMod else 1.0f).clamp(1.0f)

    LOG.trace { "CritMod: $critMod" }

    return critMod
  }

  /**
   * Calculates the variance modification.
   *
   * @return A random value between 0.85 and 1.
   */
  private fun calculateVarMod(variance: Float = 0.15f): Float {
    return 1 - random.nextFloat() * variance
  }

  protected abstract fun calculateWeaponAtk(): Float
  protected abstract fun getStatusAttack(battleCtx: EntityBattleContext): Float
  protected abstract fun getBonusAttack(battleCtx: EntityBattleContext): Float
  protected abstract fun getAmmoAttack(battleCtx: EntityBattleContext): Float
  protected abstract fun getSoftDefense(battleCtx: EntityBattleContext): Float
  protected abstract fun getHardDefenseModifier(battleCtx: EntityBattleContext): Float
  protected abstract fun getAttackModifier(battleCtx: EntityBattleContext): Float
}