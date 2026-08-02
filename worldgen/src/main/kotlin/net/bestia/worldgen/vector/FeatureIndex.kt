package net.bestia.worldgen.vector

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Spatial index over feature bounding boxes, built once per vector-producing stage and then
 * immutable.
 *
 * A uniform grid of feature-ID buckets rather than an R-tree: features are broadly uniformly
 * distributed across the world, the whole set fits in RAM on every node anyway, and this is far
 * easier to keep deterministic. Features whose bounds cover an unreasonable number of cells go into
 * an overflow list instead of being smeared across the grid, so one 40 km fjord does not bloat
 * every bucket it crosses.
 *
 * Query results are always ordered by `(priority, id)` - never by traversal order - because the
 * blend result depends on stamp order and two nodes must agree on it.
 *
 * ### What it actually measures out at, and where the cost really is
 *
 * Measured across 192-cell, 256-cell and genesis worlds via `invariants`, which prints [metrics] per seed:
 *
 * | | 192 cells | 256 cells | genesis (128) |
 * |---|---|---|---|
 * | features | 4 400 - 6 300 | 8 000 - 11 400 | 4 795 |
 * | cell size | 4 825 - 6 318 m | 4 783 - 5 680 m | 3 668 m |
 * | oversized | 0 | 0 - 2 | 0 |
 * | bucket mean | 8.6 | 8.3 | 7.6 |
 * | bucket max | **1 797** | **1 792** | **1 556** |
 *
 * Two of those numbers overturn an assumption worth writing down, because it was the assumption this
 * measurement was added to check.
 *
 * **The overflow list is empty.** [MAX_CELLS_PER_FEATURE] is 256 cells and a cell is around five
 * kilometres, so a feature has to span eighty kilometres before it overflows, and almost nothing does -
 * not even a sea lane, whose KDoc on `FeatureKind.SEA_LANE` says it lives here. That claim is now known to
 * be false on worlds of this size; the `affectsHeight = false` half of its argument still stands and is
 * the half that matters. Designing around the overflow list would have been designing around nothing.
 *
 * **The real hot spot is a town.** One bucket holds 1 500 - 1 800 features, which on a 192-cell world is
 * *thirty-seven per cent of every feature in the world in one cell* - a city's buildings, streets and
 * businesses inside a five-kilometre square. Every chunk query inside a town walks that bucket and
 * bbox-tests all of it to find the handful it overlaps. It stays that way deliberately: the mean is at
 * target, the concentration is real rather than a derivation bug, and the fix - a nested grid, or an index
 * per kind - is a subsystem to buy on evidence of a cost, which the export timings have never shown. The
 * number is recorded here so that evidence is comparable when it arrives.
 */
class FeatureIndex private constructor(
  /** All features, pre-sorted by `(priority, id)`; bucket entries are indices into this list. */
  private val features: List<VectorFeature>,
  /**
   * Grid cell size in metres, [derived][deriveCellSize] from the feature count unless overridden.
   *
   * Public because it is not an implementation detail of this class: it is derived from the union of
   * *every* feature's bbox, so a stage that emits five hundred new features changes it for every other
   * feature in the world, and therefore changes which features are [oversized]. `AreaFeature` sizes its
   * extent cap against this number, and nothing else in the module could have told it what to pick.
   */
  val cellSize: Double,
  private val originX: Double,
  private val originY: Double,
  private val cols: Int,
  private val rows: Int,
  /** CSR row offsets, length `cols * rows + 1`. */
  private val bucketStart: IntArray,
  private val bucketItems: IntArray,
  private val oversized: IntArray
) {

  val size get() = features.size

  /**
   * How many features are too broad to bucket, and are therefore appended to **every** query in the world.
   *
   * The single number that says whether this index is doing anything. A world where most features are
   * oversized is a linear scan wearing a grid, and no unit test would notice - the answers stay correct,
   * they just cost O(n) each. Sea lanes live here on purpose (`affectsHeight = false`, so
   * `FeatureEvaluator` drops them immediately); anything else appearing here is worth an argument.
   */
  val oversizedCount get() = oversized.size

  /** Cells in the grid, `cols * rows`. Together with [meanBucket] this says how well the derivation aimed. */
  val cellCount get() = cols * rows

  /** The fullest bucket: the worst case a single chunk query pays before the bbox re-test. */
  val maxBucket: Int
    get() {
      var worst = 0
      for (b in 0 until cellCount) {
        val n = bucketStart[b + 1] - bucketStart[b]
        if (n > worst) worst = n
      }
      return worst
    }

  /**
   * Mean bucket occupancy over **non-empty** cells.
   *
   * Over all cells it would mostly measure how much ocean a world has, since the grid spans the union of
   * the bboxes and features cluster on land. Over the cells that hold something it measures what
   * [TARGET_FEATURES_PER_CELL] is trying to control.
   */
  val meanBucket: Double
    get() {
      var filled = 0
      var total = 0L
      for (b in 0 until cellCount) {
        val n = bucketStart[b + 1] - bucketStart[b]
        if (n > 0) {
          filled++
          total += n
        }
      }
      return if (filled == 0) 0.0 else total.toDouble() / filled
    }

  /** Every feature whose influence could reach anywhere inside [area], ordered by `(priority, id)`. */
  fun query(area: Aabb): List<VectorFeature> {
    val hits = ArrayList<Int>()

    val minCol = colOf(area.minX)
    val maxCol = colOf(area.maxX)
    val minRow = rowOf(area.minY)
    val maxRow = rowOf(area.maxY)

    for (row in minRow..maxRow) {
      for (col in minCol..maxCol) {
        val bucket = row * cols + col
        for (i in bucketStart[bucket] until bucketStart[bucket + 1]) {
          hits.add(bucketItems[i])
        }
      }
    }
    for (i in oversized) {
      hits.add(i)
    }

    if (hits.isEmpty()) return emptyList()

    // Sorting indices ascending is enough: `features` is already in (priority, id) order.
    hits.sort()

    val out = ArrayList<VectorFeature>(hits.size)
    var previous = -1
    for (i in hits) {
      if (i == previous) continue
      previous = i
      val feature = features[i]
      if (feature.bbox.intersects(area)) {
        out.add(feature)
      }
    }

    return out
  }

  private fun colOf(x: Double) =
    floor((x - originX) / cellSize).toInt().coerceIn(0, cols - 1)

  private fun rowOf(y: Double) =
    floor((y - originY) / cellSize).toInt().coerceIn(0, rows - 1)

  /** Everything above in one immutable snapshot, so a caller can print it without holding the index. */
  fun metrics() = Metrics(
    size = size,
    cellSize = cellSize,
    cols = cols,
    rows = rows,
    oversizedCount = oversizedCount,
    maxBucket = maxBucket,
    meanBucket = meanBucket
  )

  /**
   * What the index looks like from outside, for the sweep and for sizing decisions made against it.
   *
   * A snapshot rather than live accessors because [maxBucket] and [meanBucket] are O(cells) to compute
   * and a caller printing four numbers should walk the grid once, not four times.
   */
  data class Metrics(
    val size: Int,
    val cellSize: Double,
    val cols: Int,
    val rows: Int,
    val oversizedCount: Int,
    val maxBucket: Int,
    val meanBucket: Double
  ) {
    override fun toString() = "features=$size, grid=${cols}x$rows @ ${"%.0f".format(Locale.ROOT, cellSize)}m" +
        ", oversized=$oversizedCount, bucket max=$maxBucket mean=${"%.1f".format(Locale.ROOT, meanBucket)}"
  }

  override fun toString() = "FeatureIndex[${metrics()}]"

  companion object {

    /** A feature covering more grid cells than this goes into the overflow list. */
    private const val MAX_CELLS_PER_FEATURE = 256

    private const val TARGET_FEATURES_PER_CELL = 4.0

    fun empty() = build(emptyList())

    /**
     * @param cellSizeOverride grid cell size in metres; by default it is derived so that cells hold
     *   roughly [TARGET_FEATURES_PER_CELL] features
     */
    fun build(features: Collection<VectorFeature>, cellSizeOverride: Double? = null): FeatureIndex {
      val sorted = features.sortedWith(compareBy({ it.priority }, { it.id.value }))

      require(sorted.map { it.id }.toSet().size == sorted.size) {
        "Duplicate FeatureId in the index; ids must be unique across the whole world"
      }

      if (sorted.isEmpty()) {
        return FeatureIndex(
          emptyList(), 1.0, 0.0, 0.0, 1, 1, intArrayOf(0, 0), IntArray(0), IntArray(0)
        )
      }

      var bounds = sorted[0].bbox
      for (f in sorted) {
        bounds = bounds.union(f.bbox)
      }

      val cellSize = cellSizeOverride ?: deriveCellSize(bounds, sorted.size)
      val originX = bounds.minX
      val originY = bounds.minY
      val cols = max(1, ceil(bounds.width / cellSize).toInt() + 1)
      val rows = max(1, ceil(bounds.height / cellSize).toInt() + 1)

      val oversized = ArrayList<Int>()
      val counts = IntArray(cols * rows)

      fun cellRange(f: VectorFeature): IntArray {
        val minCol = floor((f.bbox.minX - originX) / cellSize).toInt().coerceIn(0, cols - 1)
        val maxCol = floor((f.bbox.maxX - originX) / cellSize).toInt().coerceIn(0, cols - 1)
        val minRow = floor((f.bbox.minY - originY) / cellSize).toInt().coerceIn(0, rows - 1)
        val maxRow = floor((f.bbox.maxY - originY) / cellSize).toInt().coerceIn(0, rows - 1)
        return intArrayOf(minCol, maxCol, minRow, maxRow)
      }

      for (i in sorted.indices) {
        val r = cellRange(sorted[i])
        val covered = (r[1] - r[0] + 1).toLong() * (r[3] - r[2] + 1).toLong()
        if (covered > MAX_CELLS_PER_FEATURE) {
          oversized.add(i)
          continue
        }
        for (row in r[2]..r[3]) {
          for (col in r[0]..r[1]) {
            counts[row * cols + col]++
          }
        }
      }

      val bucketStart = IntArray(cols * rows + 1)
      for (b in counts.indices) {
        bucketStart[b + 1] = bucketStart[b] + counts[b]
      }

      val cursor = bucketStart.copyOf()
      val bucketItems = IntArray(bucketStart[cols * rows])
      val oversizedSet = oversized.toHashSet()
      for (i in sorted.indices) {
        if (i in oversizedSet) continue
        val r = cellRange(sorted[i])
        for (row in r[2]..r[3]) {
          for (col in r[0]..r[1]) {
            val bucket = row * cols + col
            bucketItems[cursor[bucket]++] = i
          }
        }
      }

      return FeatureIndex(
        sorted, cellSize, originX, originY, cols, rows,
        bucketStart, bucketItems, oversized.toIntArray()
      )
    }

    private fun deriveCellSize(bounds: Aabb, featureCount: Int): Double {
      val area = max(1.0, bounds.width * bounds.height)
      val targetCells = max(1.0, featureCount / TARGET_FEATURES_PER_CELL)
      // Cap the grid so a sparse world with a couple of enormous features cannot allocate a
      // gigantic bucket array, and floor it so degenerate bounds - a lone point feature with no
      // influence radius - cannot produce a zero cell size.
      val cell = kotlin.math.sqrt(area / targetCells)
      return min(max(cell, 1.0), max(bounds.width, bounds.height)).coerceAtLeast(1.0)
    }
  }
}
