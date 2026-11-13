package net.bestia.zone.spellbinding

import net.bestia.zone.battle.Weapon
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.battle.status.StatusValues

/**
 * The binder as they were at the binding. A binding fires with these values even when its binder changed gear,
 * walked off or logged out since, so it never needs the binder in the world.
 */
data class BinderSnapshot(
  val level: Int,
  val attributes: StatusValues,
  val attack: Int,
  val magicAttack: Int,
  val weaponUpgradeLevel: Int,
) {

  fun toCombatValues(): CombatValues {
    return CombatValues.ofSnapshot(level, attributes, Weapon(atk = attack, matk = magicAttack, upgradeLevel = weaponUpgradeLevel))
  }

  companion object {
    fun of(level: Int, values: CombatValues): BinderSnapshot {
      return BinderSnapshot(
        level = level,
        attributes = values.attributes,
        attack = values.weapon.atk,
        magicAttack = values.weapon.matk,
        weaponUpgradeLevel = values.weapon.upgradeLevel,
      )
    }
  }
}
