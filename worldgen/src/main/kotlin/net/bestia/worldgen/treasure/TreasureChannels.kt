package net.bestia.worldgen.treasure

/** Station channels on a `FeatureKind.TREASURE_CACHE` marker. */
object TreasureChannels {

  /** Metres from the ground, or from the cave floor, down to the cache. */
  const val DEPTH = "depth"

  /** [TreasureTier] ordinal the cache holds at least, whatever its region says. */
  const val MIN_TIER = "min_tier"

  /** Index into `Chronicle.artifacts` of the relic in it, or -1. */
  const val ARTIFACT = "artifact"

  /** Index into `Chronicle.sites` of the site it was buried at. */
  const val SITE = "site"

  /** Cave floor height in metres for a hoard, NaN for a cache under open ground. */
  const val FLOOR = "floor"
}
