package net.bestia.bus.contract

/**
 * The numbers of a bestia's orders set by hand in Fine-tune. Null keeps what the courage step and the job choose.
 * The zone clamps each to its limit.
 */
data class OrderNumbers(
  /** 0 never retreats. */
  val retreatBelowHealthPct: Int? = null,
  val eatBelowStaminaPct: Int? = null,
  val healBelowHealthPct: Int? = null,
  val haulAtLoadPct: Int? = null,
  /** In tiles. */
  val areaRadius: Int? = null,
)
