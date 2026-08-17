package net.bestia.worldgen.core

import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.Quantize

/**
 * Immutable description of one world: the seed and the dimensions everything else is derived from.
 *
 * Tuning parameters for individual stages do *not* belong here - biome definitions, culture
 * profiles, business preconditions and profile parameters belong in data files that a designer can
 * change without an engineer. This holds only what the framework itself needs.
 */
data class WorldConfig(

  val seed: Long,

  /** World extent at [baseResolution]. 4096x4096 at 1 km is ~16M cells, which fits in RAM. */
  val widthCells: Int = 4096,
  val heightCells: Int = 4096,

  /** Resolution of the heightfield and everything derived from it at world scale. */
  val baseResolution: Resolution = Resolution.KILOMETRE,

  /** Sea level in metres. Elevations are absolute, not relative to this. */
  val seaLevel: Double = 0.0,

  /** Horizontal voxels per chunk edge. */
  val chunkSize: Int = 32,

  /** Vertical voxels per chunk column. */
  val chunkHeight: Int = 256,

  /** Edge length of one voxel in metres. */
  val voxelSize: Double = 1.0,

  /**
   * Width of the ocean margin forced around the world edge, in metres. Zero disables it.
   *
   * The world is finite but has to behave as though it is not: a player walking east off the eastern edge
   * arrives from the west. Making the *terrain* continuous across that seam would mean every stage running on a
   * periodic domain - wrapping Voronoi, wrapping noise, wrapping flow routing, wrapping distance transforms -
   * and, worst of all, vector features whose geometry crosses the seam, which breaks the single continuous
   * polyline that the whole seam-free design rests on.
   *
   * So the seam is *hidden* rather than removed. Force the margin below sea level and the wrap happens between
   * two stretches of featureless deep ocean, which look alike because there is nothing there to look at. The
   * cost is honest: this is not continuity, and it only works while the margin is wider than how far a player
   * can see. Narrower than the view distance and they watch the world end.
   */
  val oceanBorderOverride: Double? = null,

  /** Whether east and west are the same place. See [oceanBorderMetres]. */
  val wrapX: Boolean = true,

  /**
   * Whether north and south are the same place.
   *
   * Off by default, because unlike [wrapX] it is not free. Temperature comes from latitude and `ClimateStage`
   * makes that a linear ramp in y, so the seam is a discontinuity rather than a join: temperature jumps by
   * the world's whole latitude span, and `Winds.zonalSign` flips hemisphere with it, which reverses the
   * orographic rain pattern `BiomeStage` classifies on. A player crossing it walks from one pole into the
   * other.
   *
   * Turn it on anyway when the alternative is a wall. The discontinuity lands inside the ocean margin the
   * same mechanism uses to hide the X seam - `OceanBorder.distanceToEdge` forces all four edges underwater -
   * so what is actually reachable either side of it is featureless polar sea, where a climate that makes no
   * sense has nothing to be inconsistent with. `zone-server` runs Genesis this way.
   *
   * Making it genuinely continuous means giving latitude a periodic profile, at which point the world has two
   * equators and two poles rather than one of each. That is a bigger change than it sounds and it is not
   * needed to stop a player finding the edge.
   */
  val wrapY: Boolean = false,

  /**
   * How much finer than physical realism this world's features are.
   *
   * Most of the interesting things a world can have are gated on absolute size: a river needs about a hundred
   * square kilometres of catchment before it carries a channel, a glacier needs enough ice flux to gouge a
   * trough, a city needs a hinterland to feed it. Those numbers are right, and they are why a 128 km world
   * comes out with two rivers, no glaciers and hardly any biome variety - it is not big enough to *deserve*
   * them.
   *
   * This is the knob that says "give me them anyway". At 4 the thresholds behave as though every length were
   * four times larger than it is, so a small world gets the feature density of a big one. It is deliberately
   * unphysical, and it is the difference between a test world you can see something in and one that is a
   * plain with a stream on it. See [scaleByLength] and [scaleByArea], and [defaultDetailScaleFor] for the
   * value a world of a given size gets if none is set.
   */
  val detailScaleOverride: Double? = null
) {

  init {
    require(widthCells > 0 && heightCells > 0) { "World must have a positive extent" }
    require(chunkSize > 0 && chunkHeight > 0) { "Chunk dimensions must be positive" }
    require(voxelSize > 0.0) { "Voxel size must be positive" }
    require(detailScale > 0.0) { "detailScale must be positive, was $detailScale" }
    require(oceanBorderMetres >= 0.0) { "oceanBorderMetres must not be negative" }
    require(oceanBorderMetres * 2.0 < minOf(widthMetres, heightMetres)) {
      "An ocean border of $oceanBorderMetres m leaves no land in a " +
          "${widthMetres.toInt()}x${heightMetres.toInt()} m world"
    }
  }

  /**
   * How much finer than realism this world's features are, derived from its size unless overridden.
   *
   * **Computed, not stored, and that is deliberate.** Stored, it goes stale: `config.copy(widthCells = 128)`
   * on a config derived for 512 keeps the 512 world's scale, and the result is a small world configured as
   * though it were large - which silently produced two rivers, and did so in the invariant sweep, so the sweep
   * was passing on a config nothing would ever ship. Anything derived from a field has to be derived at the
   * point of use or it is a copy waiting to disagree with its source.
   */
  val detailScale: Double
    get() = detailScaleOverride ?: defaultDetailScaleFor(widthCells, heightCells, baseResolution)

  /** Width of the forced ocean margin, derived from the world's size unless overridden. Zero disables it. */
  val oceanBorderMetres: Double
    get() = oceanBorderOverride ?: defaultOceanBorderFor(widthCells, heightCells, baseResolution)

  val widthMetres: Double get() = widthCells * baseResolution.metresPerCell

  val heightMetres: Double get() = heightCells * baseResolution.metresPerCell

  /**
   * A hash of everything here that decides what the terrain *is*.
   *
   * Companion to `PipelineVersion`, and answering a different question. That one asks whether two parties run
   * the same generator; this asks whether they are pointing it at the same world. Both can disagree
   * independently and the remedies are not the same, so a single number could not tell them apart.
   *
   * ### What it is actually for
   *
   * A caller that persists a world has to persist enough to rebuild this config, and nothing checks that it
   * did. Storing this hash alongside the world and recomputing it from the stored row on the next boot is
   * that check: any field that matters to generation and was not written down comes back as its default, the
   * hashes disagree, and the boot stops. `wrapX`/`wrapY` were exactly that bug - they lived here, decided
   * where the coastline went, and were absent from `zone-server`'s world row for as long as the row existed.
   *
   * It cannot catch a field that is missing from *both* this hash and the storage, which is why the list
   * below is spelled out rather than derived: adding a field to the constructor and not to this hash has to
   * be a visible omission in a diff, not an invisible one.
   *
   * ### Effective values, not overrides
   *
   * [oceanBorderMetres] and [detailScale] rather than the nullable overrides they come from, because what
   * generation reads is the resolved number. A world born with an explicit 2500 m margin and one that derived
   * the same 2500 m are the same world and must hash alike.
   */
  val shapeVersion: Long
    get() = GenRng.hash(
      seed,
      widthCells.toLong(),
      heightCells.toLong(),
      baseResolution.metresPerCell.toRawBits(),
      seaLevel.toRawBits(),
      chunkSize.toLong(),
      chunkHeight.toLong(),
      voxelSize.toRawBits(),
      oceanBorderMetres.toRawBits(),
      detailScale.toRawBits(),
      if (wrapX) 1L else 0L,
      if (wrapY) 1L else 0L
    )

  /** Horizontal edge length of a chunk in metres. */
  val chunkExtent: Double get() = chunkSize * voxelSize

  /**
   * Shrinks a length that gates a feature on the world being big enough.
   *
   * A threshold of "at least this many metres" becomes easier to meet as [detailScale] rises.
   */
  fun scaleByLength(referenceMetres: Double): Double = referenceMetres / detailScale

  /**
   * Shrinks an area that gates a feature on the world being big enough.
   *
   * Squared, because an area shrinks with the square of a length. This is what takes a river's hundred square
   * kilometres of required catchment down to six at a detail scale of four.
   */
  fun scaleByArea(referenceSquareMetres: Double): Double =
    referenceSquareMetres / (detailScale * detailScale)

  val worldRegion: CellRegion
    get() = CellRegion.world(widthCells, heightCells, baseResolution)

  val worldBounds: Aabb get() = worldRegion.toWorld()

  /** World-space bounds of a chunk, before any feature corridor margin is added. */
  fun chunkBounds(chunk: ChunkPos): Aabb {
    val minX = chunk.x * chunkExtent
    val minY = chunk.y * chunkExtent
    return Aabb(minX, minY, minX + chunkExtent, minY + chunkExtent)
  }

  /**
   * Global vertical voxel index of an elevation.
   *
   * Index 0 sits at sea level and indices go negative below it, rather than measuring from an arbitrary
   * world floor. That keeps the mapping independent of how deep the deepest trench in a particular
   * world turned out to be - two worlds with different bathymetry still agree on which voxel index a
   * given elevation is, which matters the moment a cache key or a wire format carries one.
   */
  fun voxelZOf(elevation: Double): Int = Math.floor(elevation / voxelSize).toInt()

  /**
   * Which voxel column a horizontal world coordinate falls in.
   *
   * The horizontal counterpart of [voxelZOf], and **not** the same arithmetic, which is the whole reason it
   * is a named function rather than an inlined `floor(world / voxelSize)`. The coordinate is snapped to
   * [Quantize]'s millimetre grid *before* the divide, because a trunk, a shard or a landmark near a chunk
   * border is reached by two chunks through different arithmetic and the two results can differ by an ULP.
   * Plain `floor` turns that ULP into a whole voxel of disagreement, and a prop the two chunks disagree
   * about is a prop that appears twice in the world or not at all - `VegetationScatter.propsIn` decides
   * ownership with exactly this call.
   *
   * The price is a half-millimetre bias: a position in the top 0.5 mm of a voxel snaps up into the next one,
   * so its ground is read from the neighbouring column. That is a sub-millimetre placement error on a
   * metre-scale voxel, traded for a duplicate-or-missing prop, and it is the right way round.
   *
   * **Hoisted here because it had been copied five times** - `AetheriteScatter`, `CrystalScatter`,
   * `PoiProps`, `VegetationScatter` and `ChunkMaterializer.trunkSite` each carried their own `columnOf`, and
   * `Invariants.checkPropsAreWellPlaced` carried a sixth that had drifted to plain `floor`. That drift is
   * not a difference anybody can see by reading either side; it surfaced as a rare-seed invariant failure
   * blaming the heightfield, on the one prop in a world that landed within half a millimetre of a boundary.
   */
  fun voxelOf(world: Double): Long = Math.floorDiv(Quantize.toFixed(world), Quantize.toFixed(voxelSize))

  /** Which horizontal chunk covers a world coordinate, by the same convention as [voxelOf]. */
  fun chunkOf(world: Double): Int = Math.floorDiv(voxelOf(world), chunkSize.toLong()).toInt()

  /** Which vertical chunk covers an elevation. Negative below sea level. */
  fun chunkZOf(elevation: Double): Int = Math.floorDiv(voxelZOf(elevation), chunkHeight)

  /** Elevation of the bottom face of global voxel [globalZ]. */
  fun elevationOfVoxel(globalZ: Int): Double = globalZ * voxelSize

  /** Lowest global voxel index inside [chunk]. */
  fun voxelBaseOf(chunk: ChunkPos): Int = chunk.z * chunkHeight

  /** World position of the centre of voxel column ([localX], [localY]) inside [chunk]. */
  fun columnCenter(chunk: ChunkPos, localX: Int, localY: Int): Pair<Double, Double> {
    val x = chunk.x * chunkExtent + (localX + 0.5) * voxelSize
    val y = chunk.y * chunkExtent + (localY + 0.5) * voxelSize
    return x to y
  }

  companion object {

    /**
     * World extent the stage defaults were tuned against.
     *
     * 512 km, not the 4096 km the architecture document sizes for, and the difference matters. Every threshold
     * in every stage was arrived at by generating the 512 km demo world and looking at it, so 512 km is the size
     * at which those numbers are correct *by construction* - and therefore the size that must come out with a
     * detail scale of exactly one, or this scaling would silently rework the one world already known to be right.
     */
    const val REFERENCE_EXTENT_METRES = 512_000.0

    /** Detail scale beyond which a world stops looking like terrain and starts looking like noise. */
    const val MAX_DETAIL_SCALE = 8.0

    /**
     * The detail scale a world of a given extent gets when none is chosen.
     *
     * Exactly enough to undo the world's smallness, capped. A world at or above the reference extent gets 1,
     * because it is already big enough to earn its features honestly. The cap is where it stops: past about
     * eight, river networks become a fractal mat and settlements a continuous suburb, which is a different
     * kind of uninteresting from the one this exists to fix.
     */
    fun defaultDetailScaleFor(widthCells: Int, heightCells: Int, resolution: Resolution): Double {
      val shortEdge = minOf(widthCells, heightCells) * resolution.metresPerCell
      return (REFERENCE_EXTENT_METRES / shortEdge).coerceIn(1.0, MAX_DETAIL_SCALE)
    }

    /**
     * Width of the ocean margin, in metres.
     *
     * A constant, and not for want of a formula: what the margin has to beat is the *client's view distance*,
     * which is a few hundred metres and has nothing to do with how big the world is. A margin sized as a share
     * of the world was solving a problem nobody has - it made a big world's margin enormous while hiding the
     * seam no better than a small one's, because the seam is only ever seen from a few hundred metres away.
     *
     * 2.5 km is roughly an order of magnitude of headroom over the view distance, so the far shore stays out of
     * sight even if draw distance grows several times over, and it is still only a couple of minutes' swim.
     */
    const val OCEAN_BORDER_METRES = 2_500.0

    /** Largest share of the short edge the margin may take on each side. */
    const val MAX_OCEAN_BORDER_SHARE = 0.06

    /**
     * Ocean margin for a world of a given extent.
     *
     * [OCEAN_BORDER_METRES] everywhere it fits. The share cap only binds below about a 42 km world, where a
     * fixed margin would start eating the place - four edges of it, so it goes as the perimeter while the land
     * goes as the area, and a tiny test world would be all margin.
     */
    fun defaultOceanBorderFor(widthCells: Int, heightCells: Int, resolution: Resolution): Double {
      val shortEdge = minOf(widthCells, heightCells) * resolution.metresPerCell
      return minOf(OCEAN_BORDER_METRES, shortEdge * MAX_OCEAN_BORDER_SHARE)
    }
  }
}
