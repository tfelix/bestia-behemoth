package net.bestia.worldgen.voxel

import net.bestia.worldgen.civ.BuildingChannels
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.RoofShape
import net.bestia.worldgen.civ.WallChannels
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.history.SiteChannels
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.vector.Quantize
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Vertical spans of worked material over one voxel column.
 *
 * A reusable buffer rather than a list of objects, because this is filled once per column of every chunk
 * that contains a town - a city fills it a quarter of a million times per chunk pass - and allocating a
 * list per column would dominate the cost of materialising a town.
 *
 * Four spans is enough for the worst real column: a plinth, a wall below a window, a wall above it, and a
 * roof. A fifth is dropped rather than growing the buffer, on the grounds that a column wanting five spans
 * is a bug in whatever asked for them and silently reallocating would hide it.
 */
class StructureSpans {

  private val bottom = DoubleArray(CAPACITY)
  private val top = DoubleArray(CAPACITY)
  private val block = IntArray(CAPACITY)

  var count: Int = 0
    private set

  fun clear() {
    count = 0
  }

  fun add(fromElevation: Double, toElevation: Double, material: BlockType) {
    if (count >= CAPACITY) return
    if (toElevation <= fromElevation) return

    bottom[count] = fromElevation
    top[count] = toElevation
    block[count] = material.id
    count++
  }

  fun bottomOf(i: Int) = bottom[i]
  fun topOf(i: Int) = top[i]
  fun blockOf(i: Int) = block[i]

  /** The highest point any span reaches, or [Double.NaN] when there are none. */
  fun ceiling(): Double {
    if (count == 0) return Double.NaN
    var highest = top[0]
    for (i in 1 until count) highest = max(highest, top[i])
    return highest
  }

  private companion object {
    const val CAPACITY = 4
  }
}

/**
 * Everything towns and history put on top of the ground, as blocks: buildings, wall circuits, and the
 * residue of settlements that are no longer there.
 *
 * ### Why these are blocks and not terrain
 *
 * The same reason a bridge deck is. A heightfield has one height per column, and a building is a floor with
 * air above it and a roof above that - three surfaces in one column. The vector tier gets the building's
 * *footprint and floor level* right, to sub-metre precision and identically from either side of a chunk
 * border, and this turns that into masonry.
 *
 * Nothing here is chunk-seeded. Every voxel is a function of the column's world position and the feature's
 * own immutable attributes, which is what makes two chunks agree about the wall running between them. The
 * one place randomness appears - rubble scatter in a ruin - hashes the *world* position, exactly as
 * [OreVeins] does, and for the same reason.
 */
class TownStructures(features: List<VectorFeature>, private val seed: Long) {

  private class Structure(
    val footprint: FootprintFeature,
    val function: BuildingFunction,
    val storeys: Int,
    val floor: Double,
    val wall: BlockType,
    val roof: BlockType,
    val roofShape: RoofShape,
    val door: Vec2d
  ) {
    val eave: Double get() = floor + storeys * STOREY_HEIGHT
  }

  private class Wall(
    val feature: MarkerFeature,
    val baseChannel: Int,
    val heightChannel: Int,
    val thicknessChannel: Int,
    val block: BlockType
  )

  private class Site(
    val kind: SiteKind,
    val position: Vec2d,
    val radius: Double,
    val decay: Double,
    val salt: Long
  )

  private val buildings: List<Structure> = features
    .asSequence()
    .filter { it.kind == FeatureKind.BUILDING }
    .filterIsInstance<FootprintFeature>()
    .mapNotNull { footprint ->
      runCatching {
        Structure(
          footprint = footprint,
          function = BuildingFunction.entries[
            footprint.attribute(BuildingChannels.FUNCTION).toInt()
          ],
          storeys = footprint.attribute(BuildingChannels.STOREYS).toInt().coerceIn(1, 8),
          floor = footprint.attribute(BuildingChannels.FLOOR_ELEVATION),
          wall = BlockType.of(footprint.attribute(BuildingChannels.WALL_BLOCK).toInt()),
          roof = BlockType.of(footprint.attribute(BuildingChannels.ROOF_BLOCK).toInt()),
          roofShape = RoofShape.entries[footprint.attribute(BuildingChannels.ROOF_SHAPE).toInt()],
          door = Vec2d(
            footprint.attribute(BuildingChannels.DOOR_X),
            footprint.attribute(BuildingChannels.DOOR_Y)
          )
        )
      }.getOrNull()
    }
    .toList()

  private val walls: List<Wall> = features
    .asSequence()
    .filter { it.kind == FeatureKind.TOWN_WALL }
    .filterIsInstance<MarkerFeature>()
    .mapNotNull { feature ->
      runCatching {
        Wall(
          feature = feature,
          baseChannel = feature.channel(WallChannels.BASE_ELEVATION),
          heightChannel = feature.channel(WallChannels.HEIGHT),
          thicknessChannel = feature.channel(WallChannels.HALF_THICKNESS),
          block = BlockType.of(feature.stations!!.valueAt(feature.channel(WallChannels.BLOCK), 0).toInt())
        )
      }.getOrNull()
    }
    .toList()

  /**
   * Street centrelines with their carriageway half-widths, for paving.
   *
   * A street is a `REPLACE`-blended heightfield feature, so it levels the ground it runs over and then leaves
   * it *grass*, because the surface cap comes from the biome. On a map of a town that made every street
   * invisible - readable only as the gap between two rows of buildings - which is both wrong and the sort of
   * wrong that is easy to mistake for a layout that failed.
   */
  private val streets: List<Pair<PolylineFeature, Int>> = features
    .asSequence()
    .filter { it.kind == FeatureKind.STREET }
    .filterIsInstance<PolylineFeature>()
    .mapNotNull { street ->
      val channel = runCatching { street.stations.channel(Profiles.CHANNEL_HALF_WIDTH) }.getOrNull()
      if (channel == null) null else street to channel
    }
    .toList()

  private val sites: List<Site> = features
    .asSequence()
    .filter { it.kind == FeatureKind.RUIN || it.kind == FeatureKind.TOMB || it.kind == FeatureKind.MONUMENT }
    .filterIsInstance<PointMarker>()
    .mapNotNull { marker ->
      runCatching {
        Site(
          kind = when (marker.kind) {
            FeatureKind.RUIN -> SiteKind.RUIN
            FeatureKind.TOMB -> SiteKind.TOMB
            else -> SiteKind.MONUMENT
          },
          position = marker.position,
          radius = marker.attribute(SiteChannels.RADIUS),
          decay = marker.attribute(SiteChannels.DECAY).coerceIn(0.0, 1.0),
          salt = marker.id.value
        )
      }.getOrNull()
    }
    .toList()

  val isEmpty get() = buildings.isEmpty() && walls.isEmpty() && sites.isEmpty() && streets.isEmpty()

  /**
   * The paving over one column, or null where the ground keeps whatever the biome gave it.
   *
   * Replaces the surface cap rather than adding a span, because a street is not a structure standing on the
   * ground - it *is* the ground, worked. Only the carriageway is paved and not the shoulder, so the kerb is
   * where the material changes, which is what makes a street read as a street from above.
   */
  fun pavingAt(worldX: Double, worldY: Double): BlockType? {
    if (streets.isEmpty()) return null

    val at = Vec2d(worldX, worldY)
    for ((street, channel) in streets) {
      if (!street.centerline.bbox.expanded(MAX_STREET_HALF_WIDTH).contains(worldX, worldY)) continue

      val projection = street.centerline.project(at)
      if (projection.beyondEnd) continue
      if (projection.distance > street.stations.sample(channel, projection.u)) continue

      return BlockType.COBBLESTONE
    }

    return null
  }

  /**
   * Fills [into] with every span of worked material over one column.
   *
   * @param ground terrain height of this column, after every feature including the building pads. Structures
   *   stand on it, so a plinth is the gap between it and a floor the pad could not quite reach.
   */
  fun columnAt(worldX: Double, worldY: Double, ground: Double, into: StructureSpans) {
    into.clear()

    for (structure in buildings) {
      if (!structure.footprint.contains(worldX, worldY)) continue
      buildingColumn(structure, worldX, worldY, ground, into)
      // One building per column. Footprints do not overlap - lots do not - so the first hit is the answer,
      // and continuing would let a stray overlap write two roofs into the same air.
      break
    }

    for (wall in walls) {
      wallColumn(wall, worldX, worldY, into)
    }

    for (site in sites) {
      siteColumn(site, worldX, worldY, ground, into)
    }
  }

  // --- Buildings ------------------------------------------------------------------------------------

  /**
   * One column of a building: plinth, wall or floor, and roof.
   *
   * The wall ring is decided in the footprint's own axes, so a building at an angle has square corners
   * rather than the stepped diagonal a world-axis test would give it.
   */
  private fun buildingColumn(
    structure: Structure,
    worldX: Double,
    worldY: Double,
    ground: Double,
    into: StructureSpans
  ) {
    val footprint = structure.footprint
    val dx = worldX - footprint.center.x
    val dy = worldY - footprint.center.y
    val along = dx * footprint.bearing.x + dy * footprint.bearing.y
    val across = -dx * footprint.bearing.y + dy * footprint.bearing.x

    // The floor slab, and the plinth under it where the pad could not level the ground all the way.
    into.add(min(ground, structure.floor) - SLAB_THICKNESS, structure.floor, structure.wall)

    val inWall = abs(along) > footprint.halfLength - WALL_THICKNESS ||
        abs(across) > footprint.halfWidth - WALL_THICKNESS

    if (inWall && structure.function != BuildingFunction.MARKET) {
      if (isDoorway(structure, along, across)) {
        // A lintel over the opening rather than no wall at all, so the building is not open to the eaves.
        into.add(structure.floor + DOOR_HEIGHT, structure.eave, structure.wall)
      } else {
        into.add(structure.floor, structure.eave, structure.wall)
      }
    }

    // A roof over every column of the footprint, wall or not: that is what makes it a building rather than
    // a walled yard.
    val rise = roofRise(structure, along, across)
    val ridge = structure.eave + rise
    into.add(ridge - ROOF_THICKNESS, ridge, structure.roof)
  }

  /**
   * How far the roof rises above the eaves at this column.
   *
   * A gable rises across the short axis and is flat along the long one, which is what makes a row of
   * gable-fronted plots read as a row of gables. A hip rises towards a point, so a broad-fronted temple
   * does not present a blank triangle to the street.
   */
  private fun roofRise(structure: Structure, along: Double, across: Double): Double {
    val footprint = structure.footprint
    return when (structure.roofShape) {
      RoofShape.FLAT -> PARAPET
      RoofShape.GABLE ->
        ROOF_PITCH * (footprint.halfWidth - abs(across)).coerceAtLeast(0.0)
      RoofShape.HIP -> ROOF_PITCH * min(
        (footprint.halfWidth - abs(across)).coerceAtLeast(0.0),
        (footprint.halfLength - abs(along)).coerceAtLeast(0.0)
      )
    }
  }

  /**
   * Whether this column is the doorway.
   *
   * The door sits at the middle of whichever face the lot's street is on, which the building recorded as a
   * bearing. Deciding *which face* is a branch on a float, so the comparison goes through [Quantize] - the
   * discipline from the architecture document applied to something small: two chunks that disagree about
   * which face the door is on would put a doorway in one and a wall in the other.
   */
  private fun isDoorway(structure: Structure, along: Double, across: Double): Boolean {
    val footprint = structure.footprint
    val doorAlong = structure.door.x * footprint.bearing.x + structure.door.y * footprint.bearing.y
    val doorAcross = -structure.door.x * footprint.bearing.y + structure.door.y * footprint.bearing.x

    return if (Quantize.toFixed(abs(doorAlong)) >= Quantize.toFixed(abs(doorAcross))) {
      // On an end face: the door's own side of it, centred across.
      along * doorAlong > 0.0 &&
          abs(along) > footprint.halfLength - WALL_THICKNESS &&
          abs(across) <= DOOR_HALF_WIDTH
    } else {
      across * doorAcross > 0.0 &&
          abs(across) > footprint.halfWidth - WALL_THICKNESS &&
          abs(along) <= DOOR_HALF_WIDTH
    }
  }

  // --- Walls ----------------------------------------------------------------------------------------

  /**
   * One column of a town wall, with crenellations.
   *
   * The merlon pattern is a function of quantised arc length, so the same metre of wall is a merlon from
   * either side of a chunk border. Taking it from the raw float would give two chunks different answers at
   * the boundary between two merlons and leave a one-voxel notch in the parapet.
   */
  private fun wallColumn(wall: Wall, worldX: Double, worldY: Double, into: StructureSpans) {
    val stations = wall.feature.stations ?: return
    val line = wall.feature.centerline
    if (!line.bbox.expanded(MAX_WALL_HALF_THICKNESS).contains(worldX, worldY)) return

    val projection = line.project(Vec2d(worldX, worldY))
    val halfThickness = stations.sample(wall.thicknessChannel, projection.u)
    if (projection.distance > halfThickness) return
    if (projection.beyondEnd) return

    val base = stations.sample(wall.baseChannel, projection.u)
    val height = stations.sample(wall.heightChannel, projection.u)

    val merlon = (Quantize.toFixed(projection.s / MERLON_PERIOD, 1.0) % 2L) == 0L
    val top = base + if (merlon) height else height - CRENEL_DROP

    into.add(base - WALL_FOOTING, top, wall.block)
  }

  // --- What history left behind ---------------------------------------------------------------------

  private fun siteColumn(
    site: Site,
    worldX: Double,
    worldY: Double,
    ground: Double,
    into: StructureSpans
  ) {
    val dx = worldX - site.position.x
    val dy = worldY - site.position.y
    val distance = sqrt(dx * dx + dy * dy)
    if (distance > site.radius) return

    when (site.kind) {
      SiteKind.RUIN -> ruinColumn(site, worldX, worldY, ground, distance, into)
      SiteKind.TOMB -> tombColumn(site, ground, distance, into)
      SiteKind.MONUMENT -> monumentColumn(site, ground, distance, into)
      SiteKind.BATTLEFIELD -> Unit
    }
  }

  /**
   * A ruin field: scattered rubble, with the occasional stub of standing wall.
   *
   * Density falls with distance from the centre and with decay, so a town razed a century ago still has
   * walls and one razed nine hundred years ago is a scatter of stone in the grass. That the decay is a
   * *stored* number rather than recomputed from the year is what lets the materialiser be a pure function
   * of the marker.
   */
  private fun ruinColumn(
    site: Site,
    worldX: Double,
    worldY: Double,
    ground: Double,
    distance: Double,
    into: StructureSpans
  ) {
    val density = (1.0 - distance / site.radius) * (1.0 - site.decay * DECAY_THINNING)
    val roll = GenRng.hashUnit(
      seed, site.salt,
      Math.round(worldX * SCATTER_QUANTISE),
      Math.round(worldY * SCATTER_QUANTISE)
    )
    if (roll > density * RUBBLE_COVERAGE) return

    // A second, much rarer roll decides a standing stub. Two rolls rather than a height curve, because a
    // ruin is mostly ankle-deep with a few walls in it and a curve gives every pile the same shape.
    val standing = GenRng.hashUnit(
      seed, site.salt + 1,
      Math.round(worldX * SCATTER_QUANTISE),
      Math.round(worldY * SCATTER_QUANTISE)
    ) < STANDING_CHANCE * (1.0 - site.decay)

    val height = if (standing) STUB_HEIGHT * (1.0 - site.decay * 0.5) else RUBBLE_HEIGHT
    into.add(ground - SLAB_THICKNESS, ground + height, if (standing) BlockType.MASONRY else BlockType.RUBBLE)
  }

  /** A barrow: an earth mound with a stone doorway in it. */
  private fun tombColumn(site: Site, ground: Double, distance: Double, into: StructureSpans) {
    val fraction = distance / site.radius
    val mound = TOMB_HEIGHT * (1.0 - fraction * fraction) * (1.0 - site.decay * 0.4)
    if (mound <= 0.0) return

    val material = if (distance < site.radius * TOMB_DOORWAY_SHARE) BlockType.MASONRY else BlockType.DIRT
    into.add(ground - SLAB_THICKNESS, ground + mound, material)
  }

  /** A standing monument: a stepped plinth with a shaft on it. */
  private fun monumentColumn(site: Site, ground: Double, distance: Double, into: StructureSpans) {
    if (distance < site.radius * MONUMENT_SHAFT_SHARE) {
      into.add(ground - SLAB_THICKNESS, ground + MONUMENT_HEIGHT * (1.0 - site.decay * 0.3), BlockType.MASONRY)
    } else {
      into.add(ground - SLAB_THICKNESS, ground + MONUMENT_PLINTH, BlockType.MASONRY)
    }
  }

  private companion object {
    /** Must match `Building.STOREY_HEIGHT`; the marker stores storeys, not metres. */
    const val STOREY_HEIGHT = 2.6

    /** Metres of wall thickness measured inwards from the footprint edge. */
    const val WALL_THICKNESS = 0.75

    const val SLAB_THICKNESS = 0.6
    const val ROOF_THICKNESS = 0.7

    /** Rise per metre of horizontal run. About forty degrees, which is what sheds snow and rain. */
    const val ROOF_PITCH = 0.85

    /** A flat roof is not flat: it has a parapet, or it reads as an unfinished box. */
    const val PARAPET = 0.9

    const val DOOR_HEIGHT = 2.1
    const val DOOR_HALF_WIDTH = 0.7

    /** Metres of wall sunk below the base elevation, so a wall on uneven ground has no gap under it. */
    const val WALL_FOOTING = 1.2

    /** Metres of wall per merlon-and-crenel pair. */
    const val MERLON_PERIOD = 2.0
    const val CRENEL_DROP = 1.1

    /**
     * Widest half-thickness any wall may have.
     *
     * Used to expand the wall's own bounding box before the containment test, because a [MarkerFeature]
     * reports `corridorWidthMax` of zero - it is geometry, and geometry has no width. Without the expansion
     * a column half a metre outside the centerline's bbox is skipped and the wall has a notch in it at
     * every extremum.
     */
    const val MAX_WALL_HALF_THICKNESS = 4.0

    /** Widest carriageway a street can have, for the same bounding-box reason as the wall above. */
    const val MAX_STREET_HALF_WIDTH = 6.0

    const val SCATTER_QUANTISE = 100.0

    /** Share of a fresh ruin's area covered in rubble at its centre. */
    const val RUBBLE_COVERAGE = 0.55

    /** How much of the rubble a fully decayed ruin has lost. Never all of it: earthworks outlast everything. */
    const val DECAY_THINNING = 0.7

    const val RUBBLE_HEIGHT = 0.7
    const val STANDING_CHANCE = 0.06
    const val STUB_HEIGHT = 3.2

    const val TOMB_HEIGHT = 2.8
    const val TOMB_DOORWAY_SHARE = 0.25

    const val MONUMENT_HEIGHT = 13.0
    const val MONUMENT_PLINTH = 1.1
    const val MONUMENT_SHAFT_SHARE = 0.3
  }
}
