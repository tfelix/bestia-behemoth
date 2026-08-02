package net.bestia.worldgen.vector

import java.util.Locale
import kotlin.math.max
import kotlin.math.pow

/**
 * A cross-section that depends on where a column is relative to a closed boundary.
 *
 * The areal counterpart of [HeightProfile], and the one argument that is genuinely different is the first:
 * an area has an inside, so the profile is handed a *signed* distance rather than a lateral offset. A
 * corridor profile has to use `abs(lateral)` because the sign only says which bank; here the sign says
 * whether the column is in the lake or on the shore, which is usually the first thing the profile branches
 * on.
 */
fun interface AreaProfile {

  /**
   * @param signedDistance metres to the boundary, **negative inside**. Continuous through zero.
   * @param u station parameter around the perimeter, wrapping at the vertex count
   * @param station interpolated perimeter channel values at [u], indexed by [StationTable.channel];
   *   empty when the feature carries no perimeter table
   * @param base terrain height at this column after every lower-priority feature
   */
  fun heightAt(signedDistance: Double, u: Double, station: DoubleArray, base: Double): Double
}

/**
 * A closed area of terrain: the vector tier's third geometry, after the point and the corridor.
 *
 * [FootprintFeature]'s KDoc names the five things that wanted this and could not have it - fans, deltas,
 * lakes, coastlines and settlement outlines - and calls a general polygon "a subsystem". It is, and this
 * is that subsystem, delivered as the [Ring] plus about a hundred lines here. What made it affordable is
 * that the hard part turned out to be *containment*, not clipping or offsetting: no producer needs to
 * intersect two areas or to inset one, so none of that exists.
 *
 * ### The two size limits, which are not the same limit
 *
 * [Ring.MAX_EXTENT] is arithmetic: beyond it the fixed-point cross product overflows. [MAX_AREA_EXTENT] is
 * an order of magnitude smaller and is about the *spatial index*, which is a different failure with a
 * different symptom - not a wrong answer but a slow one, world-wide, for a reason nobody would find by
 * reading either file.
 *
 * `FeatureIndex.build` derives its cell size from the union of every feature's bounds and sends anything
 * spanning more than 256 cells to an overflow list that is appended to **every query in the world**. The
 * cell size is therefore global: a stage adding five hundred ponds shifts it for every other feature.
 * Measured on 192-cell, 256-cell and genesis worlds, it comes out between 3 668 m and 6 318 m, and it is
 * stable across world sizes because feature count grows with area. An eight-kilometre cap is at most three
 * cells per axis, nine cells in all, against a threshold of 256 - so an area can never overflow, and could
 * not even if the cell size fell to a fifth of the smallest yet measured.
 *
 * That number is measured rather than argued, which is the whole reason `FeatureIndex.metrics` exists.
 *
 * ### Areal features stay out of the raster carve
 *
 * `GlacialStage.carveInto` rasterises features by walking `outline()` and stamping a band
 * `corridorWidthMax` wide along it. On a ring that paints the rim and misses the interior entirely, which
 * would be a lake with a shore and no water in it. Inflating [corridorWidthMax] to the inradius would be a
 * lie about reach - it is documented as a bound on influence and the index trusts it - and would undo the
 * speedup that path exists for.
 *
 * It is safe by construction today, because `carveInto` filters to `BlendMode.MIN` and every area emitted
 * so far blends `MIN` only through `PointFeature`-shaped paths that never reach it. Safe by construction
 * is not the same as tested, and a subsystem that is never reached looks exactly like one that works -
 * which applies in reverse here, so `AreaFeatureTest` asserts the filter excludes it rather than trusting
 * the arrangement to survive the next edit. If a raster stage ever genuinely needs an area, the honest fix
 * is an interior scanline rasteriser, not a wider corridor.
 */
class AreaFeature(
  override val id: FeatureId,
  override val kind: FeatureKind,
  val ring: Ring,
  /**
   * The height this area imposes, or null for geometry and attributes only.
   *
   * Null is not a degenerate case - it is how a lake outline, a zoning region or a fan's extent gets
   * stored and queried without touching the terrain, exactly as [MarkerFeature] does for a point.
   */
  private val profile: AreaProfile? = null,
  /**
   * Per-vertex attributes around the boundary, as a **periodic** table with one station per ring vertex.
   *
   * Periodic because the boundary is: an open table would flatten every channel across the segment from
   * the last vertex back to the first and leave a kink there. One station per vertex because
   * [RingProjection.u] is in vertex units, and a table of any other length would sample a rotated version
   * of the attributes the producer wrote - which no assertion catches and which is visible only as a lake
   * whose deep side faces the wrong way.
   */
  val perimeter: StationTable? = null,
  /**
   * Metres beyond the boundary over which influence eases to zero.
   *
   * A shore, in effect. Wider than [FootprintFeature]'s skirt by an order of magnitude, because a building
   * pad is meant to have an edge and a pond is not: a lake that ends in a one-voxel step reads as a
   * swimming pool.
   */
  private val skirt: Double = 12.0,
  override val priority: Int = kind.defaultPriority,
  override val blend: BlendMode = BlendMode.MIN
) : VectorFeature {

  init {
    require(skirt >= 0.0) { "skirt must not be negative, was $skirt" }
    require(ring.bbox.width <= MAX_AREA_EXTENT && ring.bbox.height <= MAX_AREA_EXTENT) {
      "An area feature is capped at ${(MAX_AREA_EXTENT / 1000).toInt()} km per axis so it can never " +
          "reach FeatureIndex's oversized list, which is tested against every query in the world; " +
          "$kind was ${ring.bbox.width.toInt()}x${ring.bbox.height.toInt()} m. Something genuinely " +
          "larger wants either the raster tier or an AreaFeature.tiled(...) that shares one immutable " +
          "Ring between several features with half-open claim rectangles - which is deliberately not " +
          "built until a producer needs it."
    }
    require(perimeter == null || perimeter.periodic) {
      "$kind $id has an open perimeter table; a boundary that wraps needs a periodic one"
    }
    require(perimeter == null || perimeter.stationCount == ring.vertexCount) {
      "$kind $id has ${perimeter?.stationCount} perimeter stations for ${ring.vertexCount} ring " +
          "vertices; RingProjection.u is in vertex units, so any other count silently rotates the " +
          "attributes around the shore"
    }
  }

  /**
   * How far influence reaches beyond the geometry: the skirt, and nothing else.
   *
   * Deliberately *not* the ring's radius. This is a bound on how far past the feature's own outline a
   * column can still be affected, which for an area is exactly the shore width - the interior is not
   * "reached from" the boundary, it is inside it. See the class KDoc for why widening this to cover the
   * interior would be the wrong repair for the raster-carve problem.
   */
  override val corridorWidthMax: Double get() = skirt

  override val bbox: Aabb = ring.bbox.expanded(skirt)

  override val scratchSize: Int get() = perimeter?.channelCount ?: 0

  override val affectsHeight: Boolean get() = profile != null

  override fun evaluateColumn(
    x: Double,
    y: Double,
    base: Double,
    scratch: DoubleArray,
    sink: HeightModSink
  ) {
    val shape = profile ?: return
    if (!bbox.contains(x, y)) return

    val projection = ring.project(Vec2d(x, y))
    // The magnitude is continuous and the sign is an integer decision, so the two agree at the boundary:
    // the flip happens exactly where the magnitude passes through zero. See Ring.signedDistance.
    val inside = ring.contains(x, y)
    val signed = if (inside) -projection.distance else projection.distance

    if (!inside && signed > skirt) return

    val weight = when {
      inside || skirt == 0.0 -> 1.0
      else -> PolylineFeature.smoothstep(1.0 - signed / skirt)
    }
    if (weight <= 0.0) return

    perimeter?.sampleInto(projection.u, scratch)

    val height = shape.heightAt(signed, projection.u, scratch, base)
    if (height.isNaN()) return

    sink.add(id, priority, blend, height, weight)
  }

  /** True when the column is inside the boundary proper, ignoring the skirt. Exact; see [Ring.contains]. */
  fun contains(x: Double, y: Double): Boolean = ring.contains(x, y)

  /** The boundary as a closed polyline, so the viewer draws the shape rather than its bounding box. */
  override fun outline(): List<Polyline> = listOf(ring.asPolyline())

  fun channel(name: String): Int = table().channel(name)

  fun attribute(name: String, u: Double = 0.0): Double = table().let { it.sample(it.channel(name), u) }

  private fun table(): StationTable =
    perimeter ?: throw IllegalStateException("$kind $id carries no perimeter attributes")

  override fun toString() =
    "$kind[$id, ${ring.vertexCount} vertices, ${"%.0f".format(Locale.ROOT, ring.area)}m2 " +
        "at ${"%.0f".format(Locale.ROOT, ring.centroid.x)},${"%.0f".format(Locale.ROOT, ring.centroid.y)}]"

  companion object {

    /**
     * Largest an area feature may be along either axis, in metres. Measured, not chosen - see the class
     * KDoc and `FeatureIndex`'s.
     */
    const val MAX_AREA_EXTENT = 8_000.0
  }
}

/**
 * The areal cross-section library, mirroring [Profiles] and [RadialProfiles].
 *
 * Every parameter comes from a **station channel** rather than from a captured constructor variable, which
 * is the export rule that makes a future codec need only a profile name plus a table it already has. It
 * also means a lake can be deeper on its windward side without a second feature: the channel varies around
 * the perimeter and the profile reads it wherever the column happens to project.
 */
object AreaProfiles {

  const val CHANNEL_FLOOR_ELEVATION = Profiles.CHANNEL_FLOOR_ELEVATION
  const val CHANNEL_SURFACE_ELEVATION = Profiles.CHANNEL_SURFACE_ELEVATION
  const val CHANNEL_DEPTH = Profiles.CHANNEL_DEPTH
  const val CHANNEL_THICKNESS = "thickness"
  const val CHANNEL_MAX_CUT = "max_cut"
  const val CHANNEL_MAX_FILL = "max_fill"

  /**
   * Metres inward over which the profile eases from nothing to its full value.
   *
   * A channel rather than a constructor argument, like everything else here, and it earns that
   * immediately: a lake with a steep windward shore and a shallow leeward one is this channel varying
   * around the perimeter, not two features.
   */
  const val CHANNEL_SHORE_REACH = "shore_reach"

  /** Shape of the approach from shore to floor. 1 is a cone, 2 a dish, higher a flatter floor. */
  const val CHANNEL_FLOOR_EXPONENT = "floor_exponent"

  /**
   * A bowl: [CHANNEL_FLOOR_ELEVATION] at the deepest point, rising by [CHANNEL_DEPTH] to the shore.
   *
   * Blend with [BlendMode.MIN]. Depth is a function of how far *inside* the boundary a column is rather
   * than of its distance from a centre, and that is not a detail: a radial bowl over a crescent-shaped
   * pond would be deep only near the centroid, which for a crescent is in the bite and dry. Measuring
   * inward from the shore makes the pond the same depth all along its length, which is what a real oxbow
   * is - and it is the specific reason this profile could not just be [RadialProfiles.bowl].
   */
  fun bowl(stations: StationTable): AreaProfile {
    val floor = stations.channel(CHANNEL_FLOOR_ELEVATION)
    val depth = stations.channel(CHANNEL_DEPTH)
    val reach = stations.channel(CHANNEL_SHORE_REACH)
    val exponent = stations.channel(CHANNEL_FLOOR_EXPONENT)

    return AreaProfile { signedDistance, _, station, _ ->
      val span = station[reach]
      // Zero at the shore, one once we are `shore_reach` metres inside.
      val inward = if (span <= 0.0) 1.0 else ((-signedDistance) / span).coerceIn(0.0, 1.0)
      station[floor] + station[depth] * (1.0 - inward.pow(station[exponent]))
    }
  }

  /** A flat surface at a per-station elevation. Blend with [BlendMode.REPLACE]. */
  fun pad(stations: StationTable): AreaProfile {
    val surface = stations.channel(CHANNEL_SURFACE_ELEVATION)
    return AreaProfile { _, _, station, _ -> station[surface] }
  }

  /**
   * A terrace: pulls the ground towards the per-station surface, bounded by a cut and a fill limit.
   *
   * The areal form of [RadialProfiles.terrace], and the asymmetry between cut and fill is there for the
   * same two reasons - real earthworks cut more than they fill, and a generous fill limit lets a riverside
   * town raise the channel running through it to street level and dam its own river.
   */
  fun terrace(stations: StationTable): AreaProfile {
    val surface = stations.channel(CHANNEL_SURFACE_ELEVATION)
    val cut = stations.channel(CHANNEL_MAX_CUT)
    val fill = stations.channel(CHANNEL_MAX_FILL)

    return AreaProfile { _, _, station, base ->
      val target = station[surface]
      when {
        base > target -> max(target, base - station[cut])
        base < target -> kotlin.math.min(target, base + station[fill])
        else -> target
      }
    }
  }

  /**
   * A wedge of sediment piled on the terrain: thick at one end of the lobe, thinning to nothing at the
   * other and at the edges.
   *
   * Blend with [BlendMode.ADD]. What an alluvial fan and a delta lobe both are.
   *
   * The taper along the lobe needs no apex coordinate, which is worth stating because passing one in was
   * the first attempt and it broke ground rule 6 - a captured `Vec2d` is precisely the profile parameter a
   * codec could not reconstruct from a table. It is unnecessary because [CHANNEL_THICKNESS] varies *around
   * the perimeter* and `u` is the station of the nearest boundary point: a column near the apex projects
   * onto the flanks beside the apex, a column near the toe projects onto the toe, so reading the channel at
   * `u` already says "how thick is the lobe at my end of it". The producer draws the wedge by writing the
   * channel round the ring, which is also how it gets an asymmetric fan - the lobe the flow currently
   * favours is thicker than the ones it abandoned - for free.
   */
  fun fanWedge(stations: StationTable): AreaProfile {
    val thickness = stations.channel(CHANNEL_THICKNESS)
    val reach = stations.channel(CHANNEL_SHORE_REACH)

    return AreaProfile { signedDistance, _, station, _ ->
      val span = station[reach]
      val inward = (-signedDistance).coerceAtLeast(0.0)
      // Smoothstep rather than linear so the toe has no crease where it meets untouched ground - the same
      // reason `Profiles.moraine` uses a raised cosine.
      val edge = if (span <= 0.0) 1.0 else PolylineFeature.smoothstep((inward / span).coerceIn(0.0, 1.0))
      station[thickness] * edge
    }
  }
}
