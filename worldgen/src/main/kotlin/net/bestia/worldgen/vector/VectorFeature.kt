package net.bestia.worldgen.vector

/**
 * Stable identity of a vector feature.
 *
 * Used as the tie-breaker whenever two features have equal priority, so blend order is total and
 * never depends on the order a spatial index happened to visit them in.
 */
@JvmInline
value class FeatureId(val value: Long) : Comparable<FeatureId> {
  override fun compareTo(other: FeatureId) = value.compareTo(other.value)
  override fun toString() = "F$value"
}

/**
 * How a feature's requested height combines with what is already there.
 */
enum class BlendMode {
  /** Carve: take the lower of the two. River channels, glacial troughs, fjords. */
  MIN,

  /** Raise: take the higher of the two. Levees, dykes. */
  MAX,

  /** Overwrite. Settlement grading, road running surfaces. */
  REPLACE,

  /** Pile on top. Moraines, alluvial fans, embankments. */
  ADD
}

/**
 * The kinds of feature the pipeline emits, together with the default stamp order.
 *
 * Lowest priority is stamped first, so a river running along a deglaciated trough floor works out:
 * the trough carves the broad U, then the river cuts its narrow channel into the trough floor.
 * Junction features sit above the reaches they join, because the naive `min` of two channel
 * profiles leaves a hard crease at a confluence.
 */
enum class FeatureKind(val defaultPriority: Int) {
  /**
   * A plate boundary. Carries geometry and attributes only - it does not modify terrain. Downstream
   * stages read it for fault placement, volcanic vents and ore genesis rather than re-deriving
   * boundaries from the raster, which is what the vector tier is for.
   */
  FAULT(10),

  /**
   * A mineral deposit: geometry and attributes only. Stored sparsely because it *is* sparse - per-voxel
   * ore is materialised at chunk generation by sampling the deposit, never stored.
   */
  ORE_DEPOSIT(20),
  COASTLINE(50),
  GLACIAL_TROUGH(100),

  /** The bowl at the head of a glacial trough, where the ice began. Often holds a tarn. */
  CIRQUE(120),
  FJORD(150),
  RIVER_CHANNEL(200),
  RIVER_CONFLUENCE(250),
  ALLUVIAL_FAN(300),
  DELTA(320),
  MORAINE(350),
  LAKE(400),
  OXBOW_LAKE(420),
  ROAD(500),
  ROAD_JUNCTION(520),
  BRIDGE(550),
  SETTLEMENT_GRADING(600),

  /**
   * The settlement itself: where it is, how big, what tier. Carries no terrain effect - the grading that
   * flattens the ground under it is a separate feature, so that "there is a town here" and "the ground
   * here is level" can be reasoned about, cached and versioned independently.
   */
  SETTLEMENT(620)
}

/**
 * Receives the height modifications a feature wants to make at one column.
 *
 * A feature may emit more than one (a fjord emits its trough and its sill); it must emit them in a
 * fixed order that depends only on its own immutable data.
 */
fun interface HeightModSink {

  /**
   * @param value the height the feature wants, interpreted according to [blend]
   * @param weight blend strength in `[0,1]`; the falloff at the edge of a corridor, so features do
   *   not end in a hard rim. Zero means no influence and is free to emit.
   */
  fun add(featureId: FeatureId, priority: Int, blend: BlendMode, value: Double, weight: Double)
}

/**
 * A resolution-independent world feature that modifies terrain height.
 *
 * Features live in world space at full precision. Chunks do not own features and features know
 * nothing about chunks - that is the whole point, and it is what makes the same channel appear on
 * both sides of a chunk border without any stitching.
 *
 * Implementations must be immutable and safe to share across threads. Per-column scratch space is
 * supplied by the caller for exactly this reason.
 */
interface VectorFeature {

  val id: FeatureId

  val kind: FeatureKind

  /** World-space bounds, already expanded by the influence radius. */
  val bbox: Aabb

  /** Conservative bound on how far from its geometry this feature can reach, in metres. */
  val corridorWidthMax: Double

  /** Stamp order; lower goes first. Defaults come from [FeatureKind]. */
  val priority: Int

  val blend: BlendMode

  /** How many scratch slots [evaluateColumn] needs. */
  val scratchSize: Int

  /**
   * False for features that exist only to carry geometry and attributes for downstream stages -
   * plate boundaries, and later coastline and trade-route annotations.
   *
   * Chunk generation skips them entirely. Without this a plate boundary's bounding box, which spans
   * hundreds of kilometres, would be returned by every chunk query in the world and cost a no-op
   * call per feature per voxel column forever.
   */
  val affectsHeight: Boolean get() = true

  /**
   * Evaluates this feature at world position ([x], [y]).
   *
   * @param base the terrain height accumulated so far at this column, i.e. after every
   *   lower-priority feature has been applied
   * @param scratch caller-owned buffer of at least [scratchSize] doubles; contents on entry are
   *   undefined and it may be overwritten freely
   */
  fun evaluateColumn(x: Double, y: Double, base: Double, scratch: DoubleArray, sink: HeightModSink)

  /**
   * The feature's defining geometry, for tooling that needs to draw it - the offline viewer, a
   * debug export. Empty by default: a feature that has no meaningful outline simply shows as its
   * bounding box.
   *
   * This is deliberately *not* how terrain is generated. Generation goes through [evaluateColumn]
   * and nothing else, so a viewer can never quietly diverge from what the chunks actually contain.
   */
  fun outline(): List<Polyline> = emptyList()
}
