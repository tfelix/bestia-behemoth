package net.bestia.zoneserver.battle

import mu.KotlinLogging
import net.bestia.model.battle.Damage
import net.bestia.model.domain.AttackType
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadLocalRandom

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
class DamageCalculator {

  private val rand = ThreadLocalRandom.current()

  /**
   * This calculates the taken battle damage. Currently this is only a
   * placeholder until the real damage formula is invented.
   *
   * Please not that this method ONLY calculates the damage. If the attack is
   * controlled by a script this wont get checked by this method anymore. Only
   * raw damage calculation is performed.
   */
  fun calculateDamage(battleCtx: BattleContext): Damage {
    val baseAtk = getBaseAttack(battleCtx)
    val atkMod = getAttackModifier(battleCtx)
    val hardDefMod = getHardDefenseModifier(battleCtx)
    val critMod = getCritModifier(battleCtx)
    val softDef = getSoftDefense(battleCtx)

    val damage = Math.floor((baseAtk * atkMod * hardDefMod * critMod - softDef).toDouble()).toInt()

    LOG.trace("Damage (melee): {}.", damage)

    return Damage.getHit(damage)
  }

  private fun getBaseAttack(battleCtx: BattleContext): Float {
    val usedAttack = battleCtx.usedAttack
    val (attackPhysicalBonus, attackMagicBonus) = battleCtx.damageVariables

    val statusAtk = getStatusAttack(battleCtx)
    val varMod = if (battleCtx.damageVariables.isCriticalHit) 1f else calculateVarMod()
    val weaponAtk = calculateWeaponAtk()
    val varModRed = varMod - varMod / 2 - 0.5f
    val ammaAtk = if (usedAttack.isRanged) getAmmoAtk(battleCtx) else 0f
    val bonusAtk = (if (usedAttack.isMagic) attackMagicBonus else attackPhysicalBonus).toInt()
    val elementMod = getElementMod(battleCtx)
    val elementBonusMod = getElementBonusMod(battleCtx)

    var baseAtk = 2f * statusAtk * varMod + weaponAtk * varModRed + ammaAtk + bonusAtk.toFloat()
    baseAtk = baseAtk * elementMod * elementBonusMod
    baseAtk = Math.min(1f, baseAtk)

    LOG.trace("BaseAtk: {}.", baseAtk)

    return baseAtk
  }

  /**
   * @return The attack value based solely on the status of the entity.
   */
  private fun getStatusAttack(battleCtx: BattleContext): Float {
    val atk = battleCtx.usedAttack
    val lvMod = (battleCtx.attackerLevel / 4).toFloat()
    val sp = battleCtx.attackerStatusPoints

    val statusAtk: Float
    if (atk.isMagic) {
      statusAtk = lvMod + sp!!.strength.toFloat() + (sp.dexterity / 5).toFloat()
      LOG.trace("StatusAtk (magic): {}", statusAtk)
    } else if (atk.isRanged) {
      statusAtk = lvMod + sp!!.dexterity.toFloat() + (sp.strength / 5).toFloat()
      LOG.trace("StatusAtk (ranged physical): {}", statusAtk)
    } else {
      statusAtk = lvMod + sp!!.strength.toFloat() + (sp.dexterity / 5).toFloat()
      LOG.trace("StatusAtk (melee physical): {}", statusAtk)

    }

    return statusAtk
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
    val defStatus = battleCtx.defenderStatusPoints

    var defMod: Float

    when (atk.type) {
      AttackType.MELEE_MAGIC, AttackType.RANGED_MAGIC -> {
        defMod = 1 - (defStatus!!.magicDefense / 100f + magicDefenseMod)
        defMod = 1 - (defStatus.defense / 100f + physicalDefenseMod)
        defMod = 1f
      }
      AttackType.MELEE_PHYSICAL, AttackType.RANGED_PHYSICAL -> {
        defMod = 1 - (defStatus!!.defense / 100f + physicalDefenseMod)
        defMod = 1f
      }
      else -> defMod = 1f
    }

    defMod = defMod.between(0.05f, 1.0f)

    LOG.trace("HardDefenseMod: {}.", defMod)

    return defMod
  }

  /**
   * @return Gets the attack modifier of the attack.
   */
  private fun getAttackModifier(battleCtx: BattleContext): Float {

    val atk = battleCtx.usedAttack
    val (_, _, attackMagicMod, _, attackRangedMod, attackMeleeMod) = battleCtx.damageVariables

    val atkMod: Float

    when (atk.type) {
      AttackType.MELEE_MAGIC, AttackType.MELEE_PHYSICAL -> atkMod = attackMagicMod * attackMeleeMod
      AttackType.RANGED_MAGIC, AttackType.RANGED_PHYSICAL -> atkMod = attackRangedMod * attackRangedMod
      else -> atkMod = 1.0f
    }

    LOG.trace("AttackMod: {}.", atkMod)

    return Math.min(0f, atkMod)
  }

  /**
   * @return The critical modifier.
   */
  private fun getCritModifier(battleCtx: BattleContext): Float {

    val (_, _, _, _, _, _, _, _, criticalDamageMod, _, _, _, _, _, isCriticalHit) = battleCtx.damageVariables

    var critMod = if (isCriticalHit) 1.4f * criticalDamageMod else 1.0f
    critMod = Math.min(1.0f, critMod)

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

  // HELPER METHODS

  /**
   * Calculates the variance modification.
   *
   * @return A random value between 0.85 and 1.
   */
  private fun calculateVarMod(): Float {
    return 1 - rand.nextFloat() * 0.15f
  }
}
