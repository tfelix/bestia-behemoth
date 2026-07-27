package net.bestia.worldgen.vector

/**
 * The cross-section a linear feature wants to impose, as a pure function of lateral distance.
 *
 * A profile must depend only on its arguments. If it wants variation along the feature it takes it
 * from a station channel or from noise sampled at world position - never from a chunk seed. Chunk
 * seeded randomness is fine for scattered vegetation; inside a feature profile it is forbidden,
 * because two chunks would then disagree about the same column.
 */
fun interface HeightProfile {

  /**
   * @param lateral signed distance from the centerline in metres; positive on the left of the
   *   direction of travel. Its magnitude is the true distance to the line, so past an endpoint the
   *   sign is not meaningful - symmetric profiles should use `abs(lateral)`.
   * @param u station parameter, for profiles that need to know where along the feature they are
   *   beyond what the interpolated channels already tell them (a fjord sill, for instance)
   * @param station interpolated channel values at [u], indexed by [StationTable.channel]
   * @param base terrain height at this column after all lower-priority features
   */
  fun heightAt(lateral: Double, u: Double, station: DoubleArray, base: Double): Double
}

/**
 * The workhorse [VectorFeature]: a centerline, per-station attributes, and a cross-section profile.
 *
 * Rivers, glacial troughs, roads, moraines and fjord axes are all this class with a different
 * profile and different station channels. Adding a new linear feature type means writing a
 * [HeightProfile], not a new feature system.
 *
 * The centerline should be [Polyline.resample]d to uniform spacing before the [StationTable] is
 * built - see the note there on why uniform spacing matters for the spline.
 */
class PolylineFeature(
  override val id: FeatureId,
  override val kind: FeatureKind,
  val centerline: Polyline,
  val stations: StationTable,
  private val profile: HeightProfile,
  /**
   * Name of the station channel holding the half-width of the corridor of influence, in metres.
   * Outside it the feature is skipped entirely, so it also bounds the spatial index.
   */
  corridorChannel: String = CORRIDOR_CHANNEL,
  /**
   * Fraction of the corridor half-width over which influence fades to zero at the outer edge.
   * Without it a trough wall ends in a visible rim where the corridor stops.
   */
  private val edgeFraction: Double = 0.15,
  /**
   * Metres of overshoot past an endpoint over which influence fades out. Zero leaves the natural
   * rounded end cap that falls out of distance-to-polyline, which is continuous but bowl-shaped;
   * a river mouth wants that, a road that simply stops does not.
   */
  private val endTaper: Double = 0.0,
  override val priority: Int = kind.defaultPriority,
  override val blend: BlendMode = BlendMode.MIN
) : VectorFeature {

  private val corridorIndex = stations.channel(corridorChannel)

  override val corridorWidthMax: Double = run {
    var maxCorridor = 0.0
    for (i in 0 until stations.stationCount) {
      val v = stations.valueAt(corridorIndex, i)
      if (v > maxCorridor) maxCorridor = v
    }
    // Catmull-Rom can overshoot between control points, so leave headroom rather than clipping the
    // corridor - an underestimate here would silently cut a feature off at a chunk border.
    maxCorridor * CORRIDOR_OVERSHOOT_HEADROOM + endTaper
  }

  override val bbox: Aabb = centerline.bbox.expanded(corridorWidthMax)

  override val scratchSize: Int get() = stations.channelCount

  override fun outline() = listOf(centerline)

  init {
    require(edgeFraction >= 0.0 && edgeFraction < 1.0) {
      "edgeFraction must be in [0,1), was $edgeFraction"
    }
    require(endTaper >= 0.0) { "endTaper must not be negative, was $endTaper" }
    require(stations.stationCount == centerline.vertexCount) {
      "Station count ${stations.stationCount} does not match centerline vertex count " +
          "${centerline.vertexCount}; stations are per vertex"
    }
  }

  override fun evaluateColumn(
    x: Double,
    y: Double,
    base: Double,
    scratch: DoubleArray,
    sink: HeightModSink
  ) {
    if (!bbox.contains(x, y)) return

    val proj = centerline.project(Vec2d(x, y))
    stations.sampleInto(proj.u, scratch)

    val halfWidth = scratch[corridorIndex]
    if (halfWidth <= 0.0 || proj.distance >= halfWidth) return

    var weight = edgeFalloff(proj.distance / halfWidth)
    if (weight <= 0.0) return

    if (endTaper > 0.0 && proj.beyondEnd) {
      weight *= endFalloff(proj, x, y)
      if (weight <= 0.0) return
    }

    val lateral = if (proj.lateral < 0.0) -proj.distance else proj.distance
    val height = profile.heightAt(lateral, proj.u, scratch, base)
    if (height.isNaN()) return

    sink.add(id, priority, blend, height, weight)
  }

  /** 1 in the core of the corridor, easing to 0 over the outer [edgeFraction] of it. */
  private fun edgeFalloff(normalizedDistance: Double): Double {
    if (edgeFraction == 0.0) return 1.0
    val fromEdge = 1.0 - normalizedDistance
    if (fromEdge >= edgeFraction) return 1.0
    if (fromEdge <= 0.0) return 0.0
    return smoothstep(fromEdge / edgeFraction)
  }

  /**
   * Fades influence out over [endTaper] metres of overshoot past an end cap. Continuous by
   * construction: the overshoot is zero exactly where the query position stops projecting onto the
   * interior of the line, and the weight is 1 there.
   */
  private fun endFalloff(proj: Projection, x: Double, y: Double): Double {
    val atStart = proj.s <= 0.0
    val outward = centerline.tangentAt(if (atStart) 0.0 else centerline.length)
      .let { if (atStart) -it else it }

    val overshoot = (Vec2d(x, y) - proj.point) dot outward
    if (overshoot <= 0.0) return 1.0
    if (overshoot >= endTaper) return 0.0

    return smoothstep(1.0 - overshoot / endTaper)
  }

  override fun toString() = "$kind[$id, ${centerline.vertexCount} stations]"

  companion object {
    /** Conventional name of the corridor half-width channel. */
    const val CORRIDOR_CHANNEL = "corridor"

    private const val CORRIDOR_OVERSHOOT_HEADROOM = 1.25

    internal fun smoothstep(t: Double): Double {
      val c = t.coerceIn(0.0, 1.0)
      return c * c * (3.0 - 2.0 * c)
    }
  }
}
