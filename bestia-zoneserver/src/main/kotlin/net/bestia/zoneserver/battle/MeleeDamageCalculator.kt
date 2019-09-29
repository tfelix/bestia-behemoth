package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.battle.AttackType
import net.bestia.zoneserver.entity.component.LevelComponent
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random

private val LOG = KotlinLogging.logger { }

/**
 * This calculates the raw damage of an attack if all other variables are known.
 * Even though no member state is saved this calculator is instanced as an
 * object in order to perform some kind of strategy pattern so the damage
 * calculation can be switched during runtime.
 *
 * @author Thomas Felix
 */
@Component
class MeleeDamageCalculator(
    private val random: Random
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
        .coerceAtLeast(0)

    LOG.trace { "Damage (melee): $damage." }

    return damage
  }

  private fun getBaseAttack(battleCtx: BattleContext): Float {
    val usedAttack = battleCtx.usedAttack
    val (attackPhysicalBonus, attackMagicBonus) = battleCtx.damageVariables

    val statusAtk = getStatusAttack(battleCtx)
    val varMod = if (battleCtx.damageVariables.isCriticalHit) 1f else calculateVarMod()
    val weaponAtk = battleCtx.weaponAtk
    val varModReduced = varMod - varMod / 2
    val bonusAtk = (if (usedAttack.isMagic) attackMagicBonus else attackPhysicalBonus).toInt()
    val elementMod = getElementMod(battleCtx)

    var baseAtk = 2f * statusAtk * varMod + weaponAtk * varModReduced + bonusAtk.toFloat()
    baseAtk *= elementMod
    baseAtk = max(0f, baseAtk)

    LOG.trace { "BaseAtk: $baseAtk" }

    return baseAtk
  }

  /**
   * @return The attack value based solely on the status of the entity.
   */
  private fun getStatusAttack(battleCtx: BattleContext): Float {
    val attackerLevel = battleCtx.attacker.tryGetComponent(LevelComponent::class.java)?.level ?: 1
    val lvMod = attackerLevel / 4f
    val sp = battleCtx.attackerStatusPoints

    val statusAtk = lvMod + sp.strength + sp.dexterity / 5f
    LOG.trace { "StatusAtk (melee physical): $statusAtk" }

    return statusAtk
  }

  private fun getSoftDefense(battleCtx: BattleContext): Float {
    val defStatus = battleCtx.defenderStatusPoints
    val defenderLevel = battleCtx.defender.tryGetComponent(LevelComponent::class.java)?.level ?: 1

    val softDef = max(0f, defenderLevel / 2f + defStatus.vitality + defStatus.strength / 3f)

    LOG.trace { "softDef: $softDef" }

    return softDef
  }

  private fun getHardDefenseModifier(battleCtx: BattleContext): Float {
    val atk = battleCtx.usedAttack
    val physicalDefenseMod = battleCtx.damageVariables.physicalDefenseMod
    val magicDefenseMod = battleCtx.damageVariables.magicDefenseMod
    val defStatus = battleCtx.defenderStatusPoints

    val defMod = when (atk.type) {
      AttackType.MELEE_MAGIC -> 1 - (defStatus.magicDefense / 100f) * magicDefenseMod
      AttackType.MELEE_PHYSICAL -> 1 - (defStatus.physicalDefense / 100f) * physicalDefenseMod
      else -> 1f
    }.coerceAtLeast(0.05f)

    LOG.trace { "defMod: $defMod" }

    return defMod
  }

  /**
   * @return Gets the attack modifier of the attack.
   */
  private fun getAttackModifier(battleCtx: BattleContext): Float {
    val atk = battleCtx.usedAttack
    val dmgVars = battleCtx.damageVariables

    val atkMod = when (atk.type) {
      AttackType.MELEE_MAGIC, AttackType.MELEE_PHYSICAL -> dmgVars.attackMagicMod * dmgVars.attackMeleeMod
      else -> 1.0f
    }

    LOG.trace { "AttackMod: $atkMod" }

    return max(0f, atkMod)
  }

  /**
   * @return The critical modifier.
   */
  private fun getCritModifier(battleCtx: BattleContext): Float {
    val dmgVars = battleCtx.damageVariables

    val critMod = (if (dmgVars.isCriticalHit) 1.4f * dmgVars.criticalDamageMod else 1.0f).clamp(1.0f)

    LOG.trace { "CritMod: $critMod" }

    return critMod
  }

  private fun getElementMod(battleCtx: BattleContext): Float {
    val atkEle = battleCtx.attackElement
    val defEle = battleCtx.defenderElement

    val eleMod = ElementModifier.getModifierFloat(atkEle, defEle)
    LOG.trace("ElementMod: {}", eleMod)
    return eleMod
  }

  /**
   * Calculates the variance modification.
   *
   * @return A random value between 0.85 and 1.
   */
  private fun calculateVarMod(): Float {
    return 1 - random.nextFloat() * 0.15f
  }
}