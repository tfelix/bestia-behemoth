package net.bestia.worldgen.core

/** One world boss's story: what woke it, whom it terrorised, and how it ended. */
data class MonsterRecord(
  val index: Int,
  val boss: WorldBoss,
  val nameSeed: Long,
  val origin: MonsterOrigin,
  /** The event that woke it, or -1 for [MonsterOrigin.ANCIENT]. */
  val originEvent: Int,
  val awokeYear: Int,
  val endedYear: Int,
  val fate: MonsterFate,
  /** Figure index of who slew or sealed it, or -1. */
  val hero: Int,
  /** Site index: the field it fell on, its seal, or its lair. */
  val site: Int,
  /** Artifact index of the trophy taken from it, or -1. */
  val trophy: Int
) {
  val alive get() = fate != MonsterFate.SLAIN
}
