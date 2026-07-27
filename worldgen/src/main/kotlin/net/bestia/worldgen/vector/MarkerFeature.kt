package net.bestia.worldgen.vector

/**
 * A feature that carries geometry and attributes but does not touch terrain.
 *
 * Plate boundaries are the motivating case. The tectonics stage knows exactly where every boundary
 * runs and what kind it is; six stages later the resource stage wants to put porphyry copper along
 * convergent arcs. Without a place to put that geometry the resource stage would re-derive boundaries
 * from `plate_id` by scanning the raster, at a coarser resolution, with its own tie-breaking - and
 * then two stages would disagree about where the same boundary is.
 *
 * So it goes in the vector tier like everything else, and [affectsHeight] is false so chunk
 * generation never looks at it.
 */
class MarkerFeature(
  override val id: FeatureId,
  override val kind: FeatureKind,
  val centerline: Polyline,
  /** Per-vertex attributes, or null when the geometry alone is the information. */
  val stations: StationTable? = null,
  override val priority: Int = kind.defaultPriority
) : VectorFeature {

  override val corridorWidthMax: Double get() = 0.0

  override val bbox: Aabb get() = centerline.bbox

  override val blend: BlendMode get() = BlendMode.MIN

  override val scratchSize: Int get() = 0

  override val affectsHeight: Boolean get() = false

  override fun evaluateColumn(
    x: Double,
    y: Double,
    base: Double,
    scratch: DoubleArray,
    sink: HeightModSink
  ) {
    // Nothing, by definition. Reached only if a caller bypasses `affectsHeight`.
  }

  override fun outline() = listOf(centerline)

  /**
   * Interpolated value of a station channel at the point on the centerline nearest `(x, y)`.
   *
   * The convenience that makes this type worth having: "how fast is the boundary converging here"
   * is one call, and it is the same answer for every stage that asks.
   */
  fun attributeAt(channel: Int, x: Double, y: Double): Double {
    val table = stations ?: throw IllegalStateException("$kind $id carries no station attributes")
    return table.sample(channel, centerline.project(Vec2d(x, y)).u)
  }

  fun channel(name: String): Int =
    (stations ?: throw IllegalStateException("$kind $id carries no station attributes")).channel(name)

  override fun toString() = "$kind[$id, ${centerline.vertexCount} vertices]"
}
