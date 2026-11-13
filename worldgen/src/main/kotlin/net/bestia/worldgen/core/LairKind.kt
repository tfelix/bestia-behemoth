package net.bestia.worldgen.core

/** Where a world boss hides when it withdraws. */
enum class LairKind {
  /** The deepest end of a cave system, far from daylight. */
  CAVE,

  /** Dry land as far as possible from every settlement. */
  REMOTE_LAND,

  /** Deep open sea, as far as possible from every settlement. */
  OCEAN
}
