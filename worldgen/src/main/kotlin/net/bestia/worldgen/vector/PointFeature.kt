package net.bestia.worldgen.vector

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** A cross-section that depends only on distance from a centre point. */
fun interface RadialProfile {

  /**
   * @param distance metres from the centre
   * @param base terrain height at this column after all lower-priority features
   */
  fun heightAt(distance: Double, base: Double): Double
}

/**
 * A radially symmetric feature: a disc of influence around one world position.
 *
 * The reason this exists is junctions. Two river channels meeting at a confluence are two
 * [PolylineFeature]s blended with `min`, and `min` of two parabolic cuts leaves a hard crease running
 * down the bisector of the Y - a straight line in the terrain that no river ever made. A single
 * radially symmetric bowl stamped over the junction at higher priority removes it, and because the
 * bowl is a function of distance from one fixed world point it is continuous across every chunk
 * border it happens to span, exactly like the reaches it joins.
 *
 * The same shape serves cirques, tarns, small ponds and impact craters later.
 */
class PointFeature(
  override val id: FeatureId,
  override val kind: FeatureKind,
  val center: Vec2d,
  /** Radius of influence in metres. */
  val radius: Double,
  private val profile: RadialProfile,
  /** Fraction of the radius over which influence fades out, so the disc has no visible rim. */
  private val edgeFraction: Double = 0.3,
  override val priority: Int = kind.defaultPriority,
  override val blend: BlendMode = BlendMode.MIN
) : VectorFeature {

  init {
    require(radius > 0.0) { "radius must be positive, was $radius" }
    require(edgeFraction >= 0.0 && edgeFraction < 1.0) {
      "edgeFraction must be in [0,1), was $edgeFraction"
    }
  }

  override val corridorWidthMax: Double get() = radius

  override val bbox = Aabb(center.x - radius, center.y - radius, center.x + radius, center.y + radius)

  override val scratchSize: Int get() = 0

  override fun evaluateColumn(
    x: Double,
    y: Double,
    base: Double,
    scratch: DoubleArray,
    sink: HeightModSink
  ) {
    val dx = x - center.x
    val dy = y - center.y
    val distanceSq = dx * dx + dy * dy
    if (distanceSq >= radius * radius) return

    val distance = kotlin.math.sqrt(distanceSq)
    val weight = falloff(distance / radius)
    if (weight <= 0.0) return

    val height = profile.heightAt(distance, base)
    if (height.isNaN()) return

    sink.add(id, priority, blend, height, weight)
  }

  /**
   * A ring, so the viewer draws the disc rather than only its bounding box.
   *
   * Coarse on purpose - 32 segments. It is an outline for a human, not geometry anything samples.
   */
  override fun outline(): List<Polyline> {
    val points = ArrayList<Vec2d>(OUTLINE_SEGMENTS + 1)
    for (i in 0..OUTLINE_SEGMENTS) {
      val angle = i * 2.0 * Math.PI / OUTLINE_SEGMENTS
      points.add(Vec2d(center.x + cos(angle) * radius, center.y + sin(angle) * radius))
    }
    return listOf(Polyline(points))
  }

  override fun toString() = "$kind[$id, centre=$center, r=${"%.1f".format(radius)}m]"

  private fun falloff(normalized: Double): Double {
    if (edgeFraction == 0.0) return 1.0
    val fromEdge = 1.0 - normalized
    if (fromEdge >= edgeFraction) return 1.0
    if (fromEdge <= 0.0) return 0.0
    return PolylineFeature.smoothstep(fromEdge / edgeFraction)
  }

  private companion object {
    const val OUTLINE_SEGMENTS = 32
  }
}

/** The radial cross-section library, mirroring [Profiles] for point features. */
object RadialProfiles {

  /**
   * A bowl: [floorElevation] at the centre rising by [rimHeight] to the edge.
   *
   * Blend with [BlendMode.MIN]. An exponent of 2 gives a paraboloid; higher values keep more of the
   * floor flat, which is what a confluence pool wants.
   */
  fun bowl(floorElevation: Double, rimHeight: Double, radius: Double, exponent: Double = 2.0):
      RadialProfile {
    require(radius > 0.0) { "radius must be positive, was $radius" }
    return RadialProfile { distance, _ ->
      val t = (distance / radius).coerceIn(0.0, 1.0)
      floorElevation + t.pow(exponent) * rimHeight
    }
  }

  /** A cone piled on the terrain. Blend with [BlendMode.ADD]; used for hotspot cones and tailings. */
  fun cone(height: Double, radius: Double): RadialProfile {
    require(radius > 0.0) { "radius must be positive, was $radius" }
    return RadialProfile { distance, _ ->
      height * PolylineFeature.smoothstep(1.0 - (distance / radius).coerceIn(0.0, 1.0))
    }
  }

  /** A flat pad at a fixed elevation. Blend with [BlendMode.REPLACE]; settlement grading. */
  fun pad(elevation: Double): RadialProfile = RadialProfile { _, _ -> elevation }

  /**
   * A terrace: pulls the ground towards [target], but no more than [maxCut] down and [maxFill] up.
   *
   * The asymmetry is the point, and it does two jobs at once. Real earthworks cut far more than they fill,
   * because fill has to be brought in and compacted. And a generous fill limit would let a riverside town
   * raise the channel running through it up to street level - grading is stamped after the river, so it wins
   * - which would dam every river that passes through a settlement. A couple of metres of fill levels the
   * building plots and leaves the channel alone.
   *
   * Continuous in [base], so the edge of the graded area has no step in it.
   */
  fun terrace(target: Double, maxCut: Double, maxFill: Double): RadialProfile {
    require(maxCut >= 0.0 && maxFill >= 0.0) { "cut and fill limits must not be negative" }
    return RadialProfile { _, base ->
      when {
        base > target -> max(target, base - maxCut)
        base < target -> min(target, base + maxFill)
        else -> target
      }
    }
  }
}
