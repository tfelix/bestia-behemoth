package net.bestia.worldgen.core

/** A name a settlement bore from [since] until a conquest renamed it in [until]. */
data class FormerName(
  /** Seed for `Names.place`. */
  val seed: Long,
  /** The culture `Names.place` renders [seed] in, the same one every event line of those years used. */
  val cultureIndex: Int,
  val since: Int,
  val until: Int
)
