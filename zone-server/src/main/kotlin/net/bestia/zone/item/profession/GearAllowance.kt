package net.bestia.zone.item.profession

import net.bestia.zone.battle.gear.ArmorType
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.item.persistence.Item

/**
 * What a master's professions let them use: every weapon type either allows, and the heavier armor of the two.
 * Base equipment - cloth, slings and tools - needs no profession at all.
 */
data class GearAllowance(
  val weapons: Set<WeaponType>,
  val heaviestArmor: ArmorType,
) {

  /**
   * Whether [item] is within the allowance. Novice gear ignores professions and is checked by the novice rule
   * instead. Armor is limited by its weight class: who may wear medium may wear light and cloth. A shield carries
   * an armor type and follows the same rule. Accessories have neither type and are always allowed.
   */
  fun allows(item: Item): Boolean {
    if (item.noviceOnly) {
      return true
    }

    val weaponAllowed = item.weaponType?.let { it in weapons } ?: true
    val armorAllowed = item.armorType?.let { it.ordinal <= heaviestArmor.ordinal } ?: true

    return weaponAllowed && armorAllowed
  }

  companion object {
    /** What every master may use, with or without a profession. */
    val BASE = GearAllowance(setOf(WeaponType.SLING, WeaponType.TOOL), ArmorType.CLOTH)

    fun of(professions: Collection<Profession>): GearAllowance {
      return GearAllowance(
        weapons = BASE.weapons + professions.flatMap { it.weapons },
        heaviestArmor = (professions.map { it.heaviestArmor } + BASE.heaviestArmor).maxBy { it.ordinal },
      )
    }
  }
}
