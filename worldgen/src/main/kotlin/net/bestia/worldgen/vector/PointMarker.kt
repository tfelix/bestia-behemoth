package net.bestia.worldgen.vector

/**
 * A point in world space that carries attributes but does not touch terrain.
 *
 * Ore deposits and settlement sites are the motivating cases, and the reason they live in the feature
 * store rather than in a raster is the same reason rivers do: they are *sparse*. A 4096 km world has
 * perhaps a hundred thousand deposits, and a per-cell "ore richness" layer would be sixteen million
 * cells of mostly zero, at one resolution, with the position of each deposit quantised to a kilometre.
 * As point features they cost what they actually are, they sit at full precision, and they come with a
 * spatial index for free - so "what deposits does this chunk contain" is one query.
 *
 * Attributes are a [StationTable] with a single station. That looks odd for a point, and it is
 * deliberate: it reuses the channel naming, the flat storage and the index-once-then-read pattern that
 * every other feature already uses, instead of introducing a second attribute mechanism that would drift
 * from the first.
 */
class PointMarker(
  override val id: FeatureId,
  override val kind: FeatureKind,
  val position: Vec2d,
  /** Single-station attribute row, or null when the position alone is the information. */
  val attributes: StationTable? = null,
  override val priority: Int = kind.defaultPriority
) : VectorFeature {

  init {
    require(attributes == null || attributes.stationCount == 1) {
      "A point marker has one station, not ${attributes?.stationCount}"
    }
  }

  override val corridorWidthMax: Double get() = 0.0

  override val bbox = Aabb(position.x, position.y, position.x, position.y)

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

  /**
   * No outline: a point has no extent to draw.
   *
   * Tooling falls back to drawing the bounding box, which for a point is a marker at the position - which
   * is exactly right, and needs no special case in the renderer.
   */
  override fun outline(): List<Polyline> = emptyList()

  fun channel(name: String): Int = table().channel(name)

  /** Value of a named attribute. */
  fun attribute(name: String): Double = table().let { it.sample(it.channel(name), 0.0) }

  /** Value of a pre-resolved attribute channel, for reading many markers in a loop. */
  fun attribute(channel: Int): Double = table().sample(channel, 0.0)

  private fun table(): StationTable =
    attributes ?: throw IllegalStateException("$kind $id carries no attributes")

  override fun toString() = "$kind[$id at $position]"
}
