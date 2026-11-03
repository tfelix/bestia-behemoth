package net.bestia.zone.battle.ammo

import net.bestia.zone.battle.Element

/** One shot's worth of the ammo item [itemId], as the damage formula and the on-hit effect see it. */
data class Ammunition(
  val itemId: Long,
  val attack: Int,
  val element: Element,
  val effect: AmmoEffect = AmmoEffect.NONE
)
