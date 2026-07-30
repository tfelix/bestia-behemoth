package net.bestia.worldgen.vector

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * An oriented rectangle in world space: a footprint.
 *
 * ### Why this rather than a polygon
 *
 * The vector tier has had two geometry types, a polyline and a point, and the missing third one is the
 * reason five features deviate from the design - fans, deltas, lakes, coastlines and settlement outlines
 * all want an area. A general polygon is a subsystem: it needs point-in-polygon, clipping, offsetting,
 * signed distance to an arbitrary boundary, and a spatial index that copes with concavity.
 *
 * A *rectangle* needs none of that. Two dot products put a query point in local coordinates, and the
 * signed distance to the boundary is a max of two absolute values. That is enough for the one areal thing
 * step 8 actually produces - a building - and for anything else whose shape is "a box at an angle": a
 * market stall, a field, a quay, a wall tower.
 *
 * So this is deliberately not the polygon type. It is the ninety percent of it that costs nothing, and it
 * closes the largest of the design's gaps without opening a subsystem. Fans, deltas and coastlines still
 * want a real polygon and still do not have one.
 *
 * ### Terrain, or not
 *
 * With a [profile] it flattens the ground it covers, and that is what makes a building sit on level ground
 * rather than half-buried on a slope - the architecture document's "soft deformation applied to the
 * heightfield *before* stratification". With a null profile it is geometry and attributes only, like a
 * [MarkerFeature], and chunk generation skips it.
 *
 * Doing both jobs in one feature rather than emitting a pad and a marker per building is not only about
 * count. It means the pad and the structure written on top of it can never disagree about where the
 * building is, which they could if they were two features that a later edit had to keep in step.
 */
class FootprintFeature(
  override val id: FeatureId,
  override val kind: FeatureKind,
  val center: Vec2d,
  /** Unit direction of the long axis. Normalised on construction. */
  bearing: Vec2d,
  /** Half-extent along [bearing], in metres. */
  val halfLength: Double,
  /** Half-extent across [bearing], in metres. */
  val halfWidth: Double,
  /**
   * The height this footprint imposes, or null for geometry and attributes only.
   *
   * Takes the distance *outside* the rectangle, which is zero everywhere inside it - so a pad profile
   * ignores its argument and a plinth that slopes away from the wall can use it.
   */
  private val profile: RadialProfile? = null,
  /** Per-footprint attributes, as a single-station table. Same convention as [PointMarker]. */
  val attributes: StationTable? = null,
  /**
   * Metres beyond the rectangle over which influence eases to zero.
   *
   * Small on purpose. A building pad is meant to have an edge - that edge is the doorstep - and easing it
   * over ten metres would flatten the whole street. What the skirt prevents is a one-voxel cliff at the
   * boundary between a flattened pad and untouched ground, which reads as a rendering fault.
   */
  private val skirt: Double = 1.5,
  override val priority: Int = kind.defaultPriority,
  override val blend: BlendMode = BlendMode.REPLACE
) : VectorFeature {

  val bearing: Vec2d = bearing.normalized()

  init {
    require(halfLength > 0.0 && halfWidth > 0.0) {
      "A footprint needs a positive extent, was ${halfLength}x$halfWidth"
    }
    require(skirt >= 0.0) { "skirt must not be negative, was $skirt" }
    require(attributes == null || attributes.stationCount == 1) {
      "A footprint has one station of attributes, not ${attributes?.stationCount}"
    }
    require(this.bearing.lengthSquared > 0.0) { "A footprint needs a bearing with a direction" }
  }

  /** The bearing turned ninety degrees: the across-axis. */
  private val normal: Vec2d = this.bearing.perpendicular()

  override val corridorWidthMax: Double =
    sqrt(halfLength * halfLength + halfWidth * halfWidth) + skirt

  override val bbox: Aabb = Aabb.around(corners()).expanded(skirt)

  override val scratchSize: Int get() = 0

  override val affectsHeight: Boolean get() = profile != null

  override fun evaluateColumn(
    x: Double,
    y: Double,
    base: Double,
    scratch: DoubleArray,
    sink: HeightModSink
  ) {
    val shape = profile ?: return

    val dx = x - center.x
    val dy = y - center.y
    val along = dx * bearing.x + dy * bearing.y
    val across = dx * normal.x + dy * normal.y

    // Chebyshev distance to the rectangle in its own axes: negative inside, positive outside, and the
    // largest of the two overhangs at a corner - which is what makes the skirt a rounded-off frame rather
    // than a wider rectangle.
    val outside = max(abs(along) - halfLength, abs(across) - halfWidth)
    if (outside > skirt) return

    val weight = if (outside <= 0.0 || skirt == 0.0) {
      1.0
    } else {
      PolylineFeature.smoothstep(1.0 - outside / skirt)
    }
    if (weight <= 0.0) return

    val height = shape.heightAt(max(0.0, outside), base)
    if (height.isNaN()) return

    sink.add(id, priority, blend, height, weight)
  }

  /** The four corners, in order, starting from `(-length, -width)` and going counter-clockwise. */
  fun corners(): List<Vec2d> {
    val alongVec = bearing * halfLength
    val acrossVec = normal * halfWidth
    return listOf(
      center - alongVec - acrossVec,
      center + alongVec - acrossVec,
      center + alongVec + acrossVec,
      center - alongVec + acrossVec
    )
  }

  /** The rectangle as a closed polyline, so the viewer draws the building rather than a bounding box. */
  override fun outline(): List<Polyline> {
    val ring = corners()
    return listOf(Polyline(ring + ring.first()))
  }

  /** True when the world position lies inside the rectangle proper, ignoring the skirt. */
  fun contains(x: Double, y: Double): Boolean {
    val dx = x - center.x
    val dy = y - center.y
    return abs(dx * bearing.x + dy * bearing.y) <= halfLength &&
        abs(dx * normal.x + dy * normal.y) <= halfWidth
  }

  fun channel(name: String): Int = table().channel(name)

  fun attribute(name: String): Double = table().let { it.sample(it.channel(name), 0.0) }

  fun attribute(channel: Int): Double = table().sample(channel, 0.0)

  private fun table(): StationTable =
    attributes ?: throw IllegalStateException("$kind $id carries no attributes")

  override fun toString() =
    "$kind[$id at $center, ${"%.1f".format(halfLength * 2)}x${"%.1f".format(halfWidth * 2)}m]"
}
