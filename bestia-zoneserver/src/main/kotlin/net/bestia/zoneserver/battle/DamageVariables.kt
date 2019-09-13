package net.bestia.zoneserver.battle

import net.bestia.model.battle.Element

/**
 * A set of variables which are used to modify a running damage calculation
 * operation. These variable set is piped through a script environment and can
 * be modified by attack scripts or by all equipment scripts for each incoming
 * attack.
 *
 * @author Thomas Felix
 */
data class DamageVariables(
    // Attack
    var attackMagicBonus: Float = 0f,
    var attackMagicMod: Float = 1f,
    var attackPhysicalBonus: Float = 0f,
    var attackPhysicalMod: Float = 1f,
    var attackRangedMod: Float = 1f,
    var attackMeleeMod: Float = 1f,

    // Weapon
    var weaponMod: Float = 1f,

    // Critical
    var criticalChanceMod: Float = 1f,
    var criticalDamageMod: Float = 1f,

    // Defenses
    var physicalDefenseMod: Float = 1f,
    var magicDefenseMod: Float = 1f,

    // Misc
    var neededManaMod: Float = 1f,
    var attackRangeMod: Float = 1f,
    var attackRangeBonus: Int = 0,

    var isCriticalHit: Boolean = false
) {


  /**
   * Before the variables are used a capped version must be retrieved. It is
   * not capped during calculation because some values might go into negative
   * values and can be canceled out this way without getting capped
   * beforehand.
   *
   * @return
   */
  // FIXME Das hier noch machen.
  fun capValues(): DamageVariables {
    return this
  }

  /**
   * Adds all values from the argument to the local values and return a new
   * damage variable object.
   *
   * @param rhs
   * @return
   */
  fun add(rhs: DamageVariables): DamageVariables {
    val vars = DamageVariables()

    // Attack
    vars.attackPhysicalBonus = attackPhysicalBonus + rhs.attackPhysicalBonus
    vars.attackMagicBonus = attackMagicBonus + rhs.attackMagicBonus
    vars.attackMagicMod = attackMagicMod + rhs.attackMagicMod
    vars.attackPhysicalMod = attackPhysicalMod + rhs.attackPhysicalMod
    vars.attackRangedMod = attackRangedMod + rhs.attackRangedMod
    vars.attackMeleeMod = attackRangedMod + rhs.attackRangedMod

    // Weapon
    vars.weaponMod = weaponMod + rhs.weaponMod

    // Critical
    vars.criticalChanceMod = criticalChanceMod + rhs.criticalChanceMod
    vars.criticalDamageMod = criticalDamageMod + rhs.criticalDamageMod

    // Defenses
    vars.physicalDefenseMod = criticalChanceMod + rhs.criticalChanceMod
    vars.criticalDamageMod = criticalDamageMod + rhs.criticalDamageMod
    vars.physicalDefenseMod = physicalDefenseMod + rhs.physicalDefenseMod
    vars.magicDefenseMod = magicDefenseMod + rhs.magicDefenseMod

    // Misc
    vars.neededManaMod = neededManaMod + rhs.neededManaMod
    vars.attackRangedMod = attackRangedMod + rhs.attackRangedMod
    vars.attackRangeBonus = attackRangeBonus + rhs.attackRangeBonus

    return vars
  }
}
