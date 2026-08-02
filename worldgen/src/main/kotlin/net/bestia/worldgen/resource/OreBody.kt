package net.bestia.worldgen.resource

import kotlin.math.PI

/**
 * The shape of an orebody, as a closed form both tiers agree on.
 *
 * The world tier stores a deposit as a point with a tonnage and a radius; the chunk tier decides, voxel by
 * voxel, whether a given cubic metre is ore. Those two answers have to reconcile, or the tonnage is a
 * decoration: a deposit could claim fifty tons of iron and hold five. This object is where the reconciliation
 * lives, so neither side gets to invent its own geometry.
 *
 * The body is a cylinder of radius `r` and half-height [VERTICAL_FLATTENING]`* r`, and a voxel inside it is
 * ore with probability `richness * (1 - h/r) * (1 - v/halfHeight)` - densest at the middle, fading to nothing
 * at the rim, which is what makes following a vein something a player can do. Averaging that fade over the
 * cylinder is the only integral here:
 *
 * ```
 * mean of (1 - h/r) over a disc  = 1/3
 * mean of (1 - v/hh) over a span = 1/2
 * volume                         = PI * r^2 * 2 * f * r
 * expected filled volume         = richness * (1/6) * 2 * PI * f * r^3 = richness * MEAN_FILL * r^3
 * ```
 *
 * [ResourceStage] runs it backwards - it draws a tonnage and solves for the radius that holds it - and
 * `OreVeins` runs it forwards. `Invariants` checks that the two still agree.
 */
object OreBody {

  /**
   * Vertical extent of an orebody relative to its radius. Below 1 makes it a seam rather than a blob.
   *
   * `OreVeins` reads this rather than keeping its own copy: it is half of what decides how much metal is in
   * the ground, and two constants that must be equal are one constant with a bug waiting in it.
   */
  const val VERTICAL_FLATTENING = 0.45

  /** Expected filled fraction of a body of radius 1 at richness 1, in cubic metres. See the class note. */
  const val MEAN_FILL = PI * VERTICAL_FLATTENING / 3.0

  /**
   * Smallest orebody worth finding, in metres. Below this a body is a handful of voxels a player walks past.
   */
  const val MIN_RADIUS = 18.0

  /**
   * Largest orebody, in metres.
   *
   * A cap rather than a natural limit: `ChunkMaterializer` queries features within a fixed margin of the
   * chunk it is filling, so a body wider than that margin would be missing from the chunks at its own rim.
   */
  const val MAX_RADIUS = 140.0

  /** How many voxels of ore a body of this size and concentration is expected to contain. */
  fun expectedVoxels(radius: Double, richness: Double, voxelSize: Double): Double {
    val cells = radius / voxelSize
    return MEAN_FILL * richness * cells * cells * cells
  }

  /** The radius a body needs to hold [voxels] ore voxels at [richness]. The inverse of [expectedVoxels]. */
  fun radiusForVoxels(voxels: Double, richness: Double, voxelSize: Double): Double {
    if (voxels <= 0.0 || richness <= 0.0) return 0.0
    return voxelSize * Math.cbrt(voxels / (MEAN_FILL * richness))
  }

  /** Tons of ore in a body of this size and concentration, given the yield of an average ore voxel. */
  fun tonsOf(radius: Double, richness: Double, voxelSize: Double, meanYieldKg: Double): Double =
    expectedVoxels(radius, richness, voxelSize) * meanYieldKg / KG_PER_TON

  /** The radius a body needs to hold [tons]. The inverse of [tonsOf]. */
  fun radiusForTons(tons: Double, richness: Double, voxelSize: Double, meanYieldKg: Double): Double {
    if (meanYieldKg <= 0.0) return 0.0
    return radiusForVoxels(tons * KG_PER_TON / meanYieldKg, richness, voxelSize)
  }

  /**
   * The concentration a body of this size needs to hold [tons]. The other inverse of [tonsOf].
   *
   * Used when the radius has been clamped and the tonnage has to be honoured by making the rock richer or
   * poorer instead of by making the body bigger or smaller.
   */
  fun richnessForTons(tons: Double, radius: Double, voxelSize: Double, meanYieldKg: Double): Double {
    val perUnitRichness = tonsOf(radius, 1.0, voxelSize, meanYieldKg)
    if (perUnitRichness <= 0.0) return 0.0
    return tons / perUnitRichness
  }

  private const val KG_PER_TON = 1000.0
}
