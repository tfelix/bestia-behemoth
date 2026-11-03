package net.bestia.zone.honor

import java.time.Duration

/**
 * How one layer of honor behaves over time: where it starts, how high it may go, and how fast a loss heals.
 *
 * [permanentBelow] is null for the global layer, which always heals.
 */
data class HonorLayer(
  val start: Int,
  val cap: Int,
  val recoveryPerDay: Int,
  val grace: Duration,
  val permanentBelow: Int? = null,
)
