package net.bestia.zone.battle

import net.bestia.zone.battle.ammo.AmmoType

/** What makes a weapon shoot instead of swing: how far, and which ammo it takes from the quiver. */
data class RangedWeapon(
  val range: Long,
  val ammoType: AmmoType
)
