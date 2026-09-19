package net.bestia.worldgen.civ

import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.ConvexPolygons
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.Ring
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.VectorFeature
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/** What a [FeatureKind.FIELD] carries. */
object FieldChannels {

  /** `SettlementChannels.INDEX` of the settlement that works this field. */
  const val SETTLEMENT = "settlement"

  /** Furrow direction, as its angle in radians. The one thing a field has that bare ground does not. */
  const val BEARING = "bearing"
}

/**
 * The worked ground around a settlement.
 *
 * A village is mostly its fields - they cover several times the ground the houses do, and from above they are
 * the largest thing about it. Without them a settlement is houses standing on undifferentiated grass, which
 * reads as a model of a village rather than a village.
 *
 * Partitioned by [TownPatches], which is the same Voronoi the town core uses and is general: a partition has
 * no cycles to break and no traversal to lose, so a river through the fields costs the cells it crosses and
 * not their neighbours. What differs is only the region handed to it and what is rejected afterwards.
 */
internal object TownFields {

  /**
   * Fields for one settlement, or nothing where there is no room for any.
   *
   * @param built the settlement's own outline. Nothing is sown inside it.
   * @param reach how far out the fields go, in metres from the centre.
   */
  fun of(
    frame: TownFrame,
    built: Ring,
    reach: Double,
    /** How many fields to aim for. Fewer come back where the ground refuses. */
    wanted: Int,
    channels: List<Polyline>,
    settlement: Int,
    roll: (Long, Long) -> Double,
    nextId: () -> FeatureId
  ): List<VectorFeature> {
    if (wanted < 1 || reach <= frame.builtRadius) return emptyList()

    // A ring rather than the town's own outline scaled up: the fields of a settlement follow the ground and
    // the ways, not the shape of the houses, and a scaled outline would make every field a copy of the town.
    val ground = ConvexPolygons.regular(frame.centre, reach, REACH_SIDES)

    val patches = TownPatches.of(
      frame = frame,
      core = ground,
      wantedPatches = wanted,
      channels = channels,
      roll = roll
    )

    val out = ArrayList<VectorFeature>(patches.size)
    for ((index, patch) in patches.withIndex()) {
      // The built-up area keeps its own ground. Tested at the site rather than the whole polygon because a
      // field legitimately runs up to the last house - what is rejected is a field *centred* on the village.
      if (built.contains(patch.site)) continue
      if (patch.area < MIN_AREA) continue

      // Not every cell is worked, and the further out the less of it is. Without this the partition shows
      // through as a rosette of equal fields ringing the town - which is what a Voronoi diagram looks like,
      // not what farmland does. The gaps are the rough ground, the wood and the common between holdings.
      val away = patch.site.distanceTo(frame.centre)
      val share = ((away - frame.builtRadius) / (reach - frame.builtRadius)).coerceIn(0.0, 1.0)
      if (roll(index.toLong(), FALLOW_SALT) > NEAR_KEEP + (FAR_KEEP - NEAR_KEEP) * share) continue

      // Inset off its neighbours, so what is drawn is the field rather than the partition: the gap is the
      // hedge, the headland and the track between two of them.
      val polygon = ConvexPolygons.clean(ConvexPolygons.insetAll(patch.polygon, MARGIN))
      if (polygon.size < 3 || ConvexPolygons.area(polygon) < MIN_AREA) continue

      val ring = runCatching { Ring(simplified(polygon)) }.getOrNull() ?: continue
      // Sown only where the whole field would stand: a cell half in a lake is not a half field, it is a
      // shoreline, and the buildable test is the one every other producer here already uses.
      if (ring.vertices.count { frame.buildable(it) } < ring.vertexCount - 1) continue

      out.add(
        AreaFeature(
          id = nextId(),
          kind = FeatureKind.FIELD,
          ring = ring,
          profile = null,
          perimeter = StationTable.Builder(ring.vertexCount, periodic = true)
            .channel(FieldChannels.SETTLEMENT) { settlement.toDouble() }
            .channel(FieldChannels.BEARING) { furrowOf(polygon, index, roll) }
            .build()
        )
      )
    }

    return out
  }

  /**
   * Which way a field is ploughed: along its own longest axis, jittered.
   *
   * Along the long axis because that is how it was done - the fewer headlands a team has to turn on, the less
   * of the day is spent turning - so the furrows of neighbouring fields disagree exactly as much as the fields
   * themselves do, which is what makes a patchwork rather than a texture.
   */
  private fun furrowOf(polygon: List<Vec2d>, index: Int, roll: (Long, Long) -> Double): Double {
    val axis = ConvexPolygons.orientedExtent(polygon)?.along ?: Vec2d(1.0, 0.0)
    val jitter = (roll(index.toLong(), FURROW_SALT) - 0.5) * 2.0 * FURROW_JITTER
    return atan2(axis.y, axis.x) + jitter
  }

  /** [Ring] is capped at its own vertex count, and a field has no detail worth spending them on. */
  private fun simplified(polygon: List<Vec2d>): List<Vec2d> {
    if (polygon.size <= MAX_VERTICES) return polygon
    val step = polygon.size.toDouble() / MAX_VERTICES
    return (0 until MAX_VERTICES).map { polygon[min(polygon.size - 1, (it * step).toInt())] }
  }

  /**
   * How many fields a settlement of this many buildings works.
   *
   * From the buildings rather than from the area, so a settlement's fields grow with the mouths it has to
   * feed. Floored so the smallest hamlet still has ground to work, and capped because past a point a town
   * buys its bread rather than growing it.
   */
  fun countFor(buildings: Int): Int {
    return (buildings / BUILDINGS_PER_FIELD).coerceIn(MIN_FIELDS, MAX_FIELDS)
  }

  /** How far the worked ground reaches, as a multiple of the built radius. */
  fun reachFor(builtRadius: Double): Double {
    return max(builtRadius * REACH_FACTOR, MIN_REACH)
  }

  /** Sides of the polygon the fields are cut out of. Enough that the outermost field is not visibly a chord. */
  private const val REACH_SIDES = 20

  private const val REACH_FACTOR = 2.6
  private const val MIN_REACH = 260.0

  private const val BUILDINGS_PER_FIELD = 4
  private const val MIN_FIELDS = 5
  private const val MAX_FIELDS = 26

  /** Metres left between two fields: the hedge, the headland and the way between them. */
  private const val MARGIN = 7.0

  /** Square metres below which a cell is a verge rather than a field. */
  private const val MIN_AREA = 2_500.0

  private const val MAX_VERTICES = 24

  /** How far a furrow may run off its field's own axis, in radians. About six degrees. */
  private const val FURROW_JITTER = 0.11

  /**
   * How much of the ground is actually worked, beside the town and at the far edge of its reach.
   *
   * The fall-off is the point: a settlement works what is close to it hardest, and the last fields out are
   * islands in whatever the country was doing anyway.
   */
  private const val NEAR_KEEP = 0.95
  private const val FAR_KEEP = 0.4

  private const val FURROW_SALT = 0x71L
  private const val FALLOW_SALT = 0x72L
}
