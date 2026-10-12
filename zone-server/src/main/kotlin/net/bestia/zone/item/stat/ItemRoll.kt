package net.bestia.zone.item.stat

/** What one copy rolled when it was made, and the item level that results. */
data class ItemRoll(
  val stats: List<StatValue>,
  val sockets: Int,
  val itemLevel: Int
)
