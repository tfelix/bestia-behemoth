package net.bestia.worldgen.core

/** One god of this world. Each people renders [nameSeed] in its own style, so one god has many names. */
data class DeityRecord(
  val index: Int,
  val domain: Domain,
  val nameSeed: Long,
  /** Year the god came into worship: the first year for the world's first gods. */
  val year: Int,
  /** The event that brought a later god into worship, or -1 for the first gods. */
  val cause: Int
)
