package net.bestia.worldgen.coast

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * What each station of a traced shoreline is, and how wide its strand runs.
 *
 * **Every threshold here is a guess.** What is defended is the *order* the rules are asked in and the *shape*
 * of the relationships, and those are not arbitrary:
 *
 * - A delta outranks everything because it is a fact about a feature that exists, not a threshold on a field.
 * - Marsh outranks cliff because a flat sheltered shore is not a cliff whatever its rock is made of.
 * - Shingle is the last arm because it is what a shore comes out as when sediment arrives but the waves sort it
 *   coarse - the least dangerous default of the six, and the one a mis-tuned threshold does least damage with.
 * - Berm height rises with exposure while the strand narrows, because a sheltered beach is wide and low and an
 *   exposed one is narrow and steep. Keep that when the numbers move.
 */
class ShoreClassifier(
  private val params: CoastParams,
  private val seaLevel: Double,
  private val hardness: FloatLayer,
  private val sediment: FloatLayer,
  private val temperature: FloatLayer,
  private val precipitation: FloatLayer,
  private val lakeId: IntLayer,
  deltas: List<VectorFeature>,
  private val fetch: Fetch,
  private val windAt: (Double) -> Vec2d,
  private val ground: (Double, Double) -> Double
) {

  private val lobes = deltas.map { it.bbox }

  /**
   * The station table for one segment.
   *
   * @param claimStart arc length along this segment where its claim begins, in metres
   * @param claimEnd arc length where the claim ends; the claim is half-open, so the station exactly at the end
   *   belongs to the next segment and no column is answered twice
   */
  fun tableFor(line: Polyline, claimStart: Double, claimEnd: Double): StationTable {
    val points = line.points
    val kinds = DoubleArray(points.size)
    val widths = DoubleArray(points.size)
    val berms = DoubleArray(points.size)
    val slopes = DoubleArray(points.size)
    val exposures = DoubleArray(points.size)
    val supplies = DoubleArray(points.size)

    for (i in points.indices) {
      val at = points[i]
      val normal = seawardNormalAt(points, i)

      val slope = landwardSlope(at, normal)
      val exposure = fetch.exposureAt(at.x, at.y, windAt(at.y))
      val supply = sedimentSupply(at)
      val kind = classify(at, slope, exposure, supply)

      kinds[i] = kind.ordinal.toDouble()
      slopes[i] = slope
      exposures[i] = exposure
      supplies[i] = supply
      widths[i] = widthOf(kind, slope, exposure, supply)
      berms[i] = bermOf(kind, exposure)
    }

    return StationTable.Builder(points.size)
      .channel(CoastChannels.SHORE_KIND, kinds)
      .channel(CoastChannels.BEACH_WIDTH, widths)
      .channel(CoastChannels.BERM_HEIGHT, berms)
      .channel(CoastChannels.SHORE_SLOPE, slopes)
      .channel(CoastChannels.WAVE_EXPOSURE, exposures)
      .channel(CoastChannels.SEDIMENT_SUPPLY, supplies)
      .channel(CoastChannels.CLAIM_START) { claimStart }
      .channel(CoastChannels.CLAIM_END) { claimEnd }
      .build()
  }

  /**
   * Which way is out to sea, as a unit vector.
   *
   * The line's own tangent turned ninety degrees, then pointed at whichever side is lower. Reading the sides
   * rather than assuming a winding is what makes this survive a loop traced the other way round - and the
   * tracer makes no promise about which way that is.
   */
  private fun seawardNormalAt(points: List<Vec2d>, i: Int): Vec2d {
    val before = points[max(0, i - 1)]
    val after = points[min(points.size - 1, i + 1)]

    val tx = after.x - before.x
    val ty = after.y - before.y
    val length = hypot(tx, ty)
    if (length == 0.0) return Vec2d(1.0, 0.0)

    val nx = -ty / length
    val ny = tx / length
    val at = points[i]

    val outward = ground(at.x + nx * PROBE, at.y + ny * PROBE)
    val inward = ground(at.x - nx * PROBE, at.y - ny * PROBE)

    return if (outward <= inward) Vec2d(nx, ny) else Vec2d(-nx, -ny)
  }

  /** The gradient of the land behind the waterline, measured over a baseline the detail noise cannot dominate. */
  private fun landwardSlope(at: Vec2d, seaward: Vec2d): Double {
    val near = ground(at.x - seaward.x * NEAR, at.y - seaward.y * NEAR)
    val far = ground(at.x - seaward.x * FAR, at.y - seaward.y * FAR)

    return ((far - near) / (FAR - NEAR)).coerceAtLeast(0.0)
  }

  /** How much sediment arrives here: what the erosion budget deposited, plus whatever a nearby lobe is handing over. */
  private fun sedimentSupply(at: Vec2d): Double {
    val deposited = (sediment.sampleBilinear(at.x, at.y) / SEDIMENT_FULL).coerceIn(0.0, 1.0)

    var lobe = 0.0
    for (box in lobes) {
      if (!box.expanded(params.deltaReach).contains(at.x, at.y)) continue
      lobe = 1.0
      break
    }

    return (0.45 * deposited + 0.55 * lobe).coerceIn(0.0, 1.0)
  }

  private fun onALobe(at: Vec2d) = lobes.any { it.expanded(params.deltaReach).contains(at.x, at.y) }

  private fun classify(at: Vec2d, slope: Double, exposure: Double, supply: Double): ShoreKind {
    val rain = precipitation.sampleBilinear(at.x, at.y)
    val rock = hardness.sampleBilinear(at.x, at.y).coerceIn(0.0, 1.0)
    val warmth = temperature.sampleBilinear(at.x, at.y)

    return when {
      onALobe(at) -> ShoreKind.DELTA_FLAT

      slope < params.marshSlope && rain > params.marshPrecipitation && exposure < params.marshExposure ->
        ShoreKind.SALT_MARSH

      slope > params.cliffSlope && rock > params.cliffHardness -> ShoreKind.SEA_CLIFF

      exposure > params.rockyExposure && supply < params.rockySediment -> ShoreKind.ROCKY_SHORE

      supply >= params.sandSediment && exposure < params.sandExposure && warmth >= COLD_SHORE ->
        ShoreKind.SAND_BEACH

      // Shingle: sediment arrived, but exposure or cold sorted it coarse. Named as the default rather than
      // reached by an `else` with nothing said about it.
      else -> ShoreKind.SHINGLE_BEACH
    }
  }

  /**
   * How far inland the strand runs, in metres.
   *
   * Divided by the slope, so the same beach is wide on flat ground and narrow on steep - and capped by
   * [CoastParams.maxBeachWidth], which is a hard bound rather than a look: a station claiming more than the
   * chunk query margin would put holes in its own beach.
   */
  private fun widthOf(kind: ShoreKind, slope: Double, exposure: Double, supply: Double): Double {
    val base = when (kind) {
      ShoreKind.SAND_BEACH -> (14.0 + 55.0 * supply) * (1.0 - 0.5 * exposure)
      ShoreKind.SHINGLE_BEACH -> 0.45 * (14.0 + 55.0 * supply) * (1.0 - 0.5 * exposure)
      ShoreKind.DELTA_FLAT -> 30.0 + 130.0 * supply
      ShoreKind.SALT_MARSH -> 40.0 + 180.0 * (1.0 - exposure)
      ShoreKind.ROCKY_SHORE -> 6.0 * (1.0 - exposure)
      ShoreKind.SEA_CLIFF -> 0.0
    }

    if (base <= 0.0) return 0.0

    val widened = base / max(SLOPE_FLOOR, slope / REFERENCE_SLOPE)
    return widened.coerceIn(0.0, params.maxBeachWidth)
  }

  private fun bermOf(kind: ShoreKind, exposure: Double): Double = when (kind) {
    ShoreKind.SAND_BEACH -> 0.8 + 2.4 * exposure
    ShoreKind.SHINGLE_BEACH -> 1.2 + 3.4 * exposure
    ShoreKind.DELTA_FLAT -> 0.3
    ShoreKind.SALT_MARSH -> 0.4
    ShoreKind.ROCKY_SHORE, ShoreKind.SEA_CLIFF -> 0.0
  }

  private companion object {

    /** Metres either side of the line used to decide which way is out to sea. */
    const val PROBE = 40.0

    /** The baseline the landward slope is measured over: past the detail noise, short of the next landform. */
    const val NEAR = 30.0
    const val FAR = 150.0

    /** Metres of deposited sediment that counts as a full supply. */
    const val SEDIMENT_FULL = 4.0

    /** The slope the widths above are quoted at. */
    const val REFERENCE_SLOPE = 0.04

    /** Keeps a shore with no measurable slope from claiming an unbounded strand before the cap sees it. */
    const val SLOPE_FLOOR = 0.25

    /** Below this mean annual temperature a shore weathers to shingle whatever its sediment supply. */
    const val COLD_SHORE = 2.0
  }
}
