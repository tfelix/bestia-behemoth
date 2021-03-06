package net.bestia.zoneserver.battle.damage

import kotlin.math.max

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

    var attackRangedBonus: Float = 0f,
    var attackRangedMod: Float = 1f,

    var attackMeleeBonus: Int = 0,
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
  fun limitValues() {
    attackMagicMod = max(0f, attackMagicMod)
    attackPhysicalMod = max(0f, attackPhysicalMod)
    attackRangedMod = max(0f, attackRangedMod)
    attackMeleeMod = max(0f, attackMeleeMod)

    // Critical
    criticalChanceMod = max(0f, criticalChanceMod)
    criticalDamageMod = max(0f, criticalDamageMod)

    // Defenses
    physicalDefenseMod = max(0f, physicalDefenseMod)
    magicDefenseMod = max(0f, magicDefenseMod)

    // Misc
    neededManaMod = max(0f, neededManaMod)
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
    vars.attackRangedBonus = attackRangedBonus + rhs.attackRangedBonus

    return vars
  }
}
