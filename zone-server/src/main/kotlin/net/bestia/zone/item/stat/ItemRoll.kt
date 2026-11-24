package net.bestia.zone.item.stat

import net.bestia.zone.item.essence.Essence

/** What one copy rolled when it was made, and the item level that results. */
data class ItemRoll(
  val stats: List<StatValue>,
  val sockets: Int,
  val itemLevel: Int,

  /** What an Essence Gem holds from the moment it is etched. It travels with the roll, so it reaches every copy. */
  val heldEssence: Essence? = null
)
