package net.bestia.worldgen.vector

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
 */
class FeatureIndex private constructor(
  /** All features, pre-sorted by `(priority, id)`; bucket entries are indices into this list. */
  private val features: List<VectorFeature>,
  private val cellSize: Double,
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

  override fun toString() = "FeatureIndex[features=${features.size}, grid=${cols}x$rows @ ${cellSize}m]"

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
