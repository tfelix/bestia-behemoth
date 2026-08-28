package net.bestia.worldgen.place

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.vector.Vec2d

/**
 * One named area of the world, and enough of its character to explain the name.
 *
 * The name is a **rendered string**, unlike everywhere else in the generator, where a name is a
 * 48-bit seed and `history/Names.kt` is the other half of it. Two reasons it is resolved here:
 *
 * - The only consumer is `zone-server`, which has to hand a string to a client that cannot render one
 *   (`Names` is Kotlin, the client is C#, and porting the word pools would give the two sides a way to
 *   disagree). A seed reaching the client would have to become a string somewhere anyway.
 * - Uniqueness is decided across the whole set at build time, so the strings exist by then regardless.
 *
 * [nameSeed] is kept beside it so a tool can re-render or vary the name, and so the region joins the
 * same seed vocabulary every other named thing in the world uses.
 */
data class PlaceRegion(
  /** Dense index from zero, and the value stored in the assignment grid. */
  val index: Int,

  /** The Poisson seed the region grew from. Not the centroid - growth is cost-weighted, so they differ. */
  val seed: Vec2d,

  /** Mean position of the cells actually assigned, which is where a label belongs. */
  val centre: Vec2d,

  val cellCount: Int,

  val kind: RegionKind,

  val name: String,

  val nameSeed: Long,

  /** Index into `Culture.ALL`, or -1 where no settlement is near enough to have named the place. */
  val cultureIndex: Int,

  /** Whether this is a water region. Never mixed: the partition cannot cross a coastline. */
  val isWater: Boolean,

  /** Share of the region that is dry land. Either near 0 or near 1, by construction. */
  val landShare: Double,

  val meanElevation: Double,

  /** p95 minus p5 of elevation, in metres. How much of a place this is vertically. */
  val relief: Double,

  val dominantBiome: Biome
)
