package net.bestia.zone.battle.damage

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
  var healMod: Float = 1f
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
   * Field-wise sum of two sets.
   *
   * Written as one constructor call rather than a sequence of assignments because the assignment version had
   * drifted: `attackMeleeMod` was being taken from `attackRangedMod`, `physicalDefenseMod` from
   * `criticalChanceMod` (overwritten two lines later, so only the dead line was wrong), `attackMeleeBonus` and
   * `healMod` were not copied at all, and three fields were assigned twice. Naming every parameter makes a
   * missing one a compile error instead.
   *
   * **The `*Mod` fields do not have a sensible zero for this.** They are multipliers defaulting to `1f`, so
   * summing two untouched sets yields `2f` - "no change" twice over reading as "double". Nothing calls this
   * yet; whoever wires up stacking effects has to decide whether mods multiply or whether they carry deltas
   * around zero, and that decision belongs with them rather than being guessed here.
   */
  fun add(rhs: DamageVariables): DamageVariables = DamageVariables(
    attackMagicBonus = attackMagicBonus + rhs.attackMagicBonus,
    attackMagicMod = attackMagicMod + rhs.attackMagicMod,

    attackPhysicalBonus = attackPhysicalBonus + rhs.attackPhysicalBonus,
    attackPhysicalMod = attackPhysicalMod + rhs.attackPhysicalMod,

    attackRangedBonus = attackRangedBonus + rhs.attackRangedBonus,
    attackRangedMod = attackRangedMod + rhs.attackRangedMod,

    attackMeleeBonus = attackMeleeBonus + rhs.attackMeleeBonus,
    attackMeleeMod = attackMeleeMod + rhs.attackMeleeMod,

    weaponMod = weaponMod + rhs.weaponMod,

    criticalChanceMod = criticalChanceMod + rhs.criticalChanceMod,
    criticalDamageMod = criticalDamageMod + rhs.criticalDamageMod,

    physicalDefenseMod = physicalDefenseMod + rhs.physicalDefenseMod,
    magicDefenseMod = magicDefenseMod + rhs.magicDefenseMod,

    neededManaMod = neededManaMod + rhs.neededManaMod,
    healMod = healMod + rhs.healMod
  )
}
