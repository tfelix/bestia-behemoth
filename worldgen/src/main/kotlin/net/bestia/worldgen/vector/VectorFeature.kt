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

  /**
   * A cave network: where it is, how big, how deep. One per system, at the centroid of its passages.
   *
   * Geometry and attributes only, like [ORE_DEPOSIT] and for the same reason - nothing about a cave is a
   * *shape of the ground*. The passages are carved out of the voxels at chunk generation by reading the
   * features below, and a per-cell "cave here" field would be sixteen million cells to say the same thing a
   * few hundred markers say exactly.
   */
  CAVE_SYSTEM(22),

  /**
   * One gallery: a centerline with a floor, a height and a half-width along it.
   *
   * A stored polyline rather than a hashed 3D density field, and the reason is the river seam theorem
   * verbatim. Two chunks either side of a border project their columns onto **the same continuous
   * centerline** and get the same answer, with no communication and no shared cache; a density field would
   * agree only as far as its own interpolation did. It also gets connectivity for free - a walk is connected
   * by construction, where a field gives disconnected blobs and needs a separate pass to join them.
   */
  CAVE_PASSAGE(24),

  /**
   * Where a gallery reaches daylight: the one place the carve is allowed to break the surface.
   *
   * Its own kind rather than a flag on the passage, because it is the thing every other system asks about -
   * a player looking for a way in, the viewer drawing where caves are, history hiding something down there -
   * and none of those want to walk a polyline to find out whether one end of it happens to be open.
   */
  CAVE_ENTRANCE(26),

  COASTLINE(50),

  /**
   * A tectonic closed basin: a graben or an interior sag, and the lake in it.
   *
   * Geometry and attributes only. The subsidence itself is carved into the coarse raster, because a basin is
   * five to twenty kilometres across and needs no sub-cell precision - see `geo/ClosedBasins.kt`. This marker
   * exists so the invariants and the viewer can see where one is, which is what the *first* lake source turned
   * out to need.
   *
   * Below [GLACIAL_TROUGH] in priority because it is the older and broader landform: ice and rivers cut into a
   * basin, not the other way round.
   */
  TECTONIC_BASIN(80),
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

  /**
   * A shipping route between two coastal settlements, across water a road cannot cross.
   *
   * Geometry and attributes only, and deliberately a [MarkerFeature] rather than a [PolylineFeature]. Two
   * reasons, and the first is the load-bearing one: a lane has no cross-section to stamp - a ship leaves no
   * mark on the sea floor - so there is nothing for a heightfield to do with it. The second is that a lane's
   * bounding box spans an ocean, which puts it in `FeatureIndex`'s oversized list where it is tested against
   * every query in the world; `affectsHeight` being false means `FeatureEvaluator` discards it immediately
   * instead of evaluating a corridor across half the map.
   *
   * Priority is therefore inert, and sits beside [ROAD] because that is what it is the water half of.
   */
  SEA_LANE(505),

  ROAD_JUNCTION(520),
  BRIDGE(550),
  SETTLEMENT_GRADING(600),

  /**
   * The settlement itself: where it is, how big, what tier. Carries no terrain effect - the grading that
   * flattens the ground under it is a separate feature, so that "there is a town here" and "the ground
   * here is level" can be reasoned about, cached and versioned independently.
   */
  SETTLEMENT(620),

  /**
   * What history did to a settlement: when it was founded, who owns it, how often it was sacked.
   *
   * A separate marker from [SETTLEMENT] rather than more channels on it, because the two are produced by
   * different stages and a stage may not amend another stage's output. They are joined on the settlement
   * index, which is a small integer and therefore exactly representable in a station channel - unlike a
   * [FeatureId], which is a 64-bit hash and would lose its low bits to a double.
   */
  SETTLEMENT_HISTORY(625),

  /** The economic summary of a settlement, plus the seed its households expand from. */
  SETTLEMENT_ECONOMY(628),

  /** A settlement history destroyed or emptied. Carries a decay parameter; the ground is still graded. */
  RUIN(630),
  BATTLEFIELD(632),
  MONUMENT(634),

  /** The resting place of a notable figure, and often of the artifact they were buried with. */
  TOMB(636),

  /**
   * The four sites a civilisation builds on purpose, as opposed to the residue above.
   *
   * Between [TOMB] and [STREET] in priority because they are structures standing on graded ground, exactly as
   * a monument is - and none of them modifies the heightfield, so the ordering is about where the blocks go
   * rather than about what shape the ground is.
   */
  MINE(637),
  MONASTERY(638),
  FORT(639),
  LIGHTHOUSE(641),

  /**
   * A street inside a settlement: a road with a narrower cross-section and a higher priority.
   *
   * Above [SETTLEMENT_GRADING] because a street is cut into the graded ground, not under it, and above
   * [ROAD] because where an approach road enters the town it becomes the town's street.
   */
  STREET(640),

  /**
   * A stretch of town wall between two gates.
   *
   * Geometry and attributes only. A wall is a structure standing on the ground rather than a shape of the
   * ground, so like a bridge deck it cannot live in a heightfield - the materialiser lays it as blocks
   * from the base elevation the stations record. Gates are simply the gaps between one stretch and the
   * next, so no chunk has to reconcile two features to know where the opening is.
   */
  TOWN_WALL(650),
  GATE(652),

  /** One building: an oriented footprint, a floor elevation, a function, and a grammar seed. */
  BUILDING(660),

  /** A business occupying a building. Where the innkeeper is, once there is an innkeeper. */
  BUSINESS(670),

  /** An inn on a road, a day's travel from anywhere. Not attached to a settlement. */
  ROADSIDE_INN(672),

  /**
   * Valuables hidden in a cave, and never collected.
   *
   * The [CAVE_SYSTEM] / [CAVE_HOARD] pair is the [SETTLEMENT] / [SETTLEMENT_HISTORY] pattern one level down:
   * the terrain stage says where the caves are, the history simulation says what somebody put in one, and
   * neither amends the other's output. They are joined on the cave system's dense index.
   *
   * This is the only site marker that carries an **elevation**, because it is the only one that is not on the
   * ground. Whatever spawns the treasure needs all three coordinates.
   */
  CAVE_HOARD(674)
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
