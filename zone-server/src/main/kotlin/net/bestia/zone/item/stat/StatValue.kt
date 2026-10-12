package net.bestia.zone.item.stat

/** One stat on one item, such as +3 STR. */
data class StatValue(
  val stat: ItemStat,
  val value: Int
)
