package net.bestia.worldgen.treasure

import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest

/** Tuning for [TreasureStage] and the wild lattice in [TreasureCaches]. */
data class TreasureParams(

  /** Chance that a history site of each kind buried a cache. A site holding a relic always did. */
  val tombChance: Double = 0.7,
  val ruinChance: Double = 0.5,
  val outpostChance: Double = 0.2,

  /**
   * A battlefield holds caches by its dead rather than by chance: this many per dead on a fresh field, at least
   * one and at most [battlefieldMaxCaches]. The dead dropped them, so they lie shallow.
   */
  val battlefieldCachesPerDead: Double = 0.008,
  val battlefieldMaxCaches: Int = 12,
  val battlefieldMaxDepth: Double = 1.0,

  /** Every other cache lies between these depths, in metres. */
  val minDepth: Double = 0.5,
  val maxDepth: Double = 4.0,

  /**
   * Edge of the wild lattice, in metres. Each cell holds at most one cache.
   *
   * **Not a thing to change after a world ships**: the cell is the durable half of a wild cache's id.
   */
  val wildCellSize: Double = 62.5,

  /** Wild caches per square kilometre on ordinary land. */
  val wildPerSquareKm: Double = 16.0,

  /** How much denser the wild caches lie near old camps and at the foot of cliffs. */
  val hotspotBoost: Double = 4.0,

  /** Metres from a camp within which the ground counts as near it. */
  val campReach: Double = 150.0,

  /** A spot is at a cliff foot when the ground this far away rises by [cliffRise] or more. */
  val cliffProbe: Double = 8.0,
  val cliffRise: Double = 5.0
) : Params {

  init {
    require(tombChance in 0.0..1.0 && ruinChance in 0.0..1.0) { "chances must be in [0,1]" }
    require(outpostChance in 0.0..1.0) { "chances must be in [0,1]" }
    require(battlefieldCachesPerDead >= 0.0 && battlefieldMaxCaches >= 1) { "a battlefield holds at least one cache" }
    require(minDepth > 0.0 && maxDepth >= minDepth) { "depths must be positive and ordered" }
    require(battlefieldMaxDepth in minDepth..maxDepth) { "battlefieldMaxDepth must lie between the depths" }
    require(wildCellSize > 0.0) { "wildCellSize must be positive, was $wildCellSize" }
    require(wildPerSquareKm >= 0.0) { "wildPerSquareKm must not be negative, was $wildPerSquareKm" }
    require(hotspotBoost >= 1.0) { "hotspotBoost must be at least 1, was $hotspotBoost" }
    require(wildShare * hotspotBoost <= 1.0) { "a cell cannot hold more than one cache" }
    require(campReach >= 0.0 && cliffProbe > 0.0 && cliffRise > 0.0) { "cliff and camp reach must be positive" }
  }

  /** Chance that one lattice cell on ordinary land holds a cache. */
  val wildShare: Double
    get() {
      return wildPerSquareKm * wildCellSize * wildCellSize / SQUARE_METRES_PER_KM2
    }

  fun depthAt(unit: Double): Double {
    return minDepth + (maxDepth - minDepth) * unit
  }

  fun battlefieldDepthAt(unit: Double): Double {
    return minDepth + (battlefieldMaxDepth - minDepth) * unit
  }

  override fun digest(): ParamsDigest {
    return ParamsDigest()
      .put("tombChance", tombChance)
      .put("ruinChance", ruinChance)
      .put("battlefieldCachesPerDead", battlefieldCachesPerDead)
      .put("battlefieldMaxCaches", battlefieldMaxCaches)
      .put("battlefieldMaxDepth", battlefieldMaxDepth)
      .put("outpostChance", outpostChance)
      .put("minDepth", minDepth)
      .put("maxDepth", maxDepth)
      .put("wildCellSize", wildCellSize)
      .put("wildPerSquareKm", wildPerSquareKm)
      .put("hotspotBoost", hotspotBoost)
      .put("campReach", campReach)
      .put("cliffProbe", cliffProbe)
      .put("cliffRise", cliffRise)
  }

  private companion object {
    const val SQUARE_METRES_PER_KM2 = 1_000_000.0
  }
}
