package net.bestia.worldgen.treasure

/**
 * One buried cache, from either source.
 *
 * @param id stable for the life of the world: what a server keys a dug cache on.
 * @param floor the height in metres of the ground, or cave floor, the cache lies under.
 * @param artifact index into `Chronicle.artifacts` of the relic in it, or -1.
 * @param site index into `Chronicle.sites` of the site it was buried at, or -1 for a wild cache.
 */
data class TreasureCache(
  val id: Long,
  val x: Double,
  val y: Double,
  val floor: Double,
  val depth: Double,
  val minTier: TreasureTier,
  val artifact: Int,
  val site: Int
) {

  val z: Double
    get() {
      return floor - depth
    }
}
