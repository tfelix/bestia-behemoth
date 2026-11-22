package net.bestia.zone.stronghold

import java.time.Instant

/**
 * One day's treasure of a Stronghold, waiting for the leader. [coins] are a promise: they leave the world reserve only
 * when collected, so a drop that is replaced or lost owes the reserve nothing.
 */
data class TreasureDrop(
  val madeAt: Instant,
  val coins: Long,
  /** Item id to amount: ore of the deposits round the seed, and crafting items. */
  val goods: Map<Long, Int>,
)
