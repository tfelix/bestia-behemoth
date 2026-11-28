package net.bestia.bus.contract

/**
 * Item ids a bestia [neverUse]s to look after itself, even where they would qualify, and those it may [alsoUse] on
 * top of common food and bandages. At most 8 in each list.
 */
data class OrderCareItems(
  val neverUse: List<Long> = emptyList(),
  val alsoUse: List<Long> = emptyList(),
)
