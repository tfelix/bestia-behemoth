package net.bestia.worldgen.voxel

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The shape of one act of removal, and how much of each voxel it takes.
 *
 * Terrain is only ever *removed* - a pick, a bestia digging into a hillside, a spell that destroys landscape -
 * so this is the only vocabulary a player has for changing the world, and it is deliberately the same one
 * generation already uses: `ChunkMaterializer.carve` reduces occupancy and never raises it, and so does this.
 * That correspondence is the point. A shaft the generator cut for a mine head and a shaft a player dug are the
 * same kind of thing, and neither one has a way to express placing material.
 *
 * ### There is a minimum size, and it is not a gameplay choice
 *
 * The client meshes terrain with surface nets over a field whose corner samples are the mean of the eight cells
 * meeting at them. That makes the field a two-cell-wide box blur of occupancy, so **a corner only falls below
 * the isolevel once more than half the material in some 2x2x2 voxel box is gone.** Carve less than that and the
 * client draws nothing at all - while this side records air, and answers line of sight and walkability through
 * rock the player can still see. That is the worst failure this class exists to prevent, because neither the
 * revision check nor `BaseHash` can catch it: the base really is that rock and the revision really did advance.
 *
 * Measured against the real mesher (`CarveVisibilityTest` in the client):
 *
 * | radius | volume removed | apparent bore |
 * |---|---|---|
 * | 1.0 | 4.2 | **nothing drawn** |
 * | 1.2 | 7.2 | **nothing drawn** |
 * | 1.3 | 9.3 | 1.1 |
 * | 1.6 | 16.9 | 2.2 |
 * | 2.0 | 33.4 | 3.3 |
 * | 3.0 | 113.3 | 5.6 |
 *
 * So **apparent bore is about `2r - 1`** - the blur eats half a voxel of radius in every direction - and there
 * is a hard cliff below about 1.3. [MIN_RADIUS] sits at 2.0, which reads as a walkable gallery and leaves real
 * margin above the cliff. **Do not lower it** without re-running that test; a radius that looks like a cheap
 * saving is a radius that mines invisibly.
 *
 * The threshold is *volumetric*, which is the trap worth naming: sub-voxel occupancy does not lower it. A
 * radius of 1.2 removes over seven cubic metres, written to a fifth of a percent of a voxel, and still draws
 * nothing, because the blur only ever sees the total. Fractional carving buys smooth walls, not visibility.
 *
 * ### A sphere is a capsule whose ends coincide
 *
 * One shape rather than two, because every removal shape wanted so far is "within `r` of a line segment": a
 * blast is a sphere, a pick stroke or a beam is a capsule, and a capsule is convex either way - which is what
 * lets [removedFractionOf] settle a fully-enclosed voxel from its eight corners alone.
 *
 * ### Units are voxels, not metres
 *
 * Everything here is in voxel units, because the resolution floor above is a property of the voxel grid and
 * comparing [MIN_RADIUS] against a distance in metres would be meaningless at any `voxelSize` but 1. A caller
 * holding metres divides by `WorldConfig.voxelSize` first.
 *
 * Horizontal coordinates are world voxel coordinates and vertical is the global voxel index, so **z is
 * routinely negative** - index 0 is sea level, not a world floor. This class does no chunk arithmetic and knows
 * nothing about the world seam; a brush is a shape in world space, and splitting one across chunk addresses is
 * the caller's job because only the caller knows how the world wraps.
 */
class CarveBrush private constructor(
  val fromX: Double,
  val fromY: Double,
  val fromZ: Double,
  val toX: Double,
  val toY: Double,
  val toZ: Double,
  val radius: Double
) {

  init {
    require(radius >= MIN_RADIUS) {
      "A carve of radius $radius is below the mesher's resolution floor of $MIN_RADIUS voxels and would " +
          "remove rock the client cannot draw"
    }
    require(fromX.isFinite() && fromY.isFinite() && fromZ.isFinite()) { "Brush start is not finite" }
    require(toX.isFinite() && toY.isFinite() && toZ.isFinite()) { "Brush end is not finite" }
  }

  /** Lowest world voxel x the brush can reach. */
  val minVoxelX: Long = floor(min(fromX, toX) - radius).toLong()
  val minVoxelY: Long = floor(min(fromY, toY) - radius).toLong()
  val minVoxelZ: Int = floor(min(fromZ, toZ) - radius).toInt()

  /** Highest world voxel x the brush can reach, inclusive. */
  val maxVoxelX: Long = ceil(max(fromX, toX) + radius).toLong() - 1
  val maxVoxelY: Long = ceil(max(fromY, toY) + radius).toLong() - 1
  val maxVoxelZ: Int = ceil(max(fromZ, toZ) + radius).toInt() - 1

  private val segmentX = toX - fromX
  private val segmentY = toY - fromY
  private val segmentZ = toZ - fromZ
  private val segmentLengthSquared = segmentX * segmentX + segmentY * segmentY + segmentZ * segmentZ

  private val radiusSquared = radius * radius

  /** Beyond this from a voxel's centre, no part of the voxel can be inside. */
  private val outsideCutoffSquared = (radius + HALF_DIAGONAL) * (radius + HALF_DIAGONAL)

  /**
   * How much of voxel ([voxelX], [voxelY], [voxelZ]) this brush removes, in `[0,1]`.
   *
   * Exact at both extremes and supersampled in between. A voxel whose eight corners are all inside is entirely
   * inside, because a capsule is convex; a voxel whose centre is further from the segment than the radius plus
   * the voxel's own half-diagonal cannot be touched at all. Only the boundary shell pays for sampling, which is
   * the shell whose fraction is the entire reason occupancy is a byte rather than a bit.
   */
  fun removedFractionOf(voxelX: Long, voxelY: Long, voxelZ: Int): Double {
    val lowX = voxelX.toDouble()
    val lowY = voxelY.toDouble()
    val lowZ = voxelZ.toDouble()

    if (distanceSquaredToSegment(lowX + 0.5, lowY + 0.5, lowZ + 0.5) > outsideCutoffSquared) return 0.0

    var cornersInside = 0
    for (cz in 0..1) {
      for (cy in 0..1) {
        for (cx in 0..1) {
          if (distanceSquaredToSegment(lowX + cx, lowY + cy, lowZ + cz) <= radiusSquared) cornersInside++
        }
      }
    }
    if (cornersInside == 8) return 1.0

    var inside = 0
    for (sz in 0 until SAMPLES) {
      val pz = lowZ + (sz + 0.5) * SAMPLE_STEP
      for (sy in 0 until SAMPLES) {
        val py = lowY + (sy + 0.5) * SAMPLE_STEP
        for (sx in 0 until SAMPLES) {
          val px = lowX + (sx + 0.5) * SAMPLE_STEP
          if (distanceSquaredToSegment(px, py, pz) <= radiusSquared) inside++
        }
      }
    }

    return inside.toDouble() / SAMPLE_COUNT
  }

  /**
   * Visits every voxel the brush takes any part of, with how much of it goes.
   *
   * Voxels it does not touch are skipped rather than reported at zero, so a caller can treat every visit as a
   * removal to record. The order is column-major with the vertical axis innermost, matching
   * [VoxelChunk.columnOffset], so a caller writing into a chunk walks memory forwards.
   */
  inline fun forEachVoxel(action: (voxelX: Long, voxelY: Long, voxelZ: Int, removed: Double) -> Unit) {
    for (voxelY in minVoxelY..maxVoxelY) {
      for (voxelX in minVoxelX..maxVoxelX) {
        for (voxelZ in minVoxelZ..maxVoxelZ) {
          val removed = removedFractionOf(voxelX, voxelY, voxelZ)
          if (removed > 0.0) action(voxelX, voxelY, voxelZ, removed)
        }
      }
    }
  }

  /**
   * Squared distance from a point to the brush's axis, clamped to the segment.
   *
   * Degenerates correctly for a sphere: a zero-length segment leaves the parameter at zero and this is the
   * distance to the centre, so the sphere case needs no branch of its own.
   */
  private fun distanceSquaredToSegment(px: Double, py: Double, pz: Double): Double {
    val offsetX = px - fromX
    val offsetY = py - fromY
    val offsetZ = pz - fromZ

    val t = if (segmentLengthSquared <= 0.0) {
      0.0
    } else {
      ((offsetX * segmentX + offsetY * segmentY + offsetZ * segmentZ) / segmentLengthSquared)
        .coerceIn(0.0, 1.0)
    }

    val dx = offsetX - t * segmentX
    val dy = offsetY - t * segmentY
    val dz = offsetZ - t * segmentZ

    return dx * dx + dy * dy + dz * dz
  }

  override fun toString() =
    if (segmentLengthSquared <= 0.0) {
      "CarveBrush[sphere r=$radius at ($fromX,$fromY,$fromZ)]"
    } else {
      "CarveBrush[capsule r=$radius ($fromX,$fromY,$fromZ)..($toX,$toY,$toZ)]"
    }

  companion object {

    /**
     * Smallest radius, in voxels, that the client can draw.
     *
     * See the class note for the measurements. Nothing renders below about 1.3; this is 2.0, which bores about
     * 3.3 voxels across and leaves margin. Lowering it reintroduces invisible mining, which presents as the
     * server and the client disagreeing about solid rock.
     */
    const val MIN_RADIUS = 2.0

    /**
     * Samples per axis when a voxel straddles the surface.
     *
     * Chosen so that **the occupancy byte, not the sampling, is what limits how smooth a tunnel wall is.** A byte
     * resolves a fraction to 1/255, or 0.39%, so the sampling has to be at least that good.
     *
     * The two are easy to conflate and are not the same thing: 512 samples put a *granularity* of 0.2% on the
     * answer, but the *accuracy* is worse, because midpoint sampling of a curved boundary is a Riemann sum whose
     * error depends on how the sphere's radius happens to land against the sample lattice. Measured against the
     * analytic volume over radii 2 to 5, the worst case runs about 1.5% at four samples per axis, is no better at
     * six (1.4% - the error oscillates with alignment rather than falling smoothly), and reaches 0.6% at eight.
     * Eight is therefore the first value where the byte is the coarser of the two, and it costs 2.4x six for a
     * pass that is already well under a millisecond a swing.
     */
    const val SAMPLES = 8

    private const val SAMPLE_STEP = 1.0 / SAMPLES
    private const val SAMPLE_COUNT = SAMPLES * SAMPLES * SAMPLES

    /** Half the body diagonal of one voxel: the most a corner can be from the centre. */
    private val HALF_DIAGONAL = sqrt(3.0) / 2.0

    /** A blast, a bore head, a spell that takes a bite out of a hillside. */
    fun sphere(x: Double, y: Double, z: Double, radius: Double) =
      CarveBrush(x, y, z, x, y, z, radius)

    /** A pick stroke or a beam: everything within [radius] of the segment. */
    fun capsule(
      fromX: Double,
      fromY: Double,
      fromZ: Double,
      toX: Double,
      toY: Double,
      toZ: Double,
      radius: Double
    ) = CarveBrush(fromX, fromY, fromZ, toX, toY, toZ, radius)
  }
}
