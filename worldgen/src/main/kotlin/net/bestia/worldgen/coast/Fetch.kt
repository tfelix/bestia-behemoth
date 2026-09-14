package net.bestia.worldgen.coast

import net.bestia.worldgen.vector.Vec2d
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How open a stretch of shore is to the waves, from how much open water lies in front of it.
 *
 * **A ray march and deliberately not a distance transform.** `fields/DistanceTransform` answers "how far to the
 * nearest land", which is isotropic and therefore cannot say *which direction* the water lies in - and the
 * direction is the entire content of fetch. A fjord head and an exposed headland can sit the same distance from
 * land and be nothing alike: one has a single long reach straight out and shelter everywhere else, the other is
 * open on every side. That difference is what decides whether a shore is a beach or a cliff, so it cannot be
 * averaged away.
 *
 * Cheap enough not to think about. A world's shore touches a few hundred to a couple of thousand kilometre
 * cells, and each costs eight rays of at most [CoastParams.fetchReach] steps - under a million array reads for
 * the whole world, memoised per cell so the several stations falling in one cell march it once.
 */
class Fetch(
  private val ocean: BooleanArray,
  private val width: Int,
  private val height: Int,
  private val metresPerCell: Double,
  private val params: CoastParams
) {

  private val cache = HashMap<Int, DoubleArray>()

  /**
   * Exposure at a world position, 0 to 1.
   *
   * @param windTo the prevailing wind direction here, which weights the rays: water upwind of a shore is what
   *   builds the waves that reach it, and the same reach downwind does not.
   */
  fun exposureAt(x: Double, y: Double, windTo: Vec2d): Double {
    val cx = floor(x / metresPerCell).toInt().coerceIn(0, width - 1)
    val cy = floor(y / metresPerCell).toInt().coerceIn(0, height - 1)

    val reaches = cache.getOrPut(cy * width + cx) { march(cx, cy) }

    val windLength = sqrt(windTo.x * windTo.x + windTo.y * windTo.y)
    val wx = if (windLength > 0.0) windTo.x / windLength else 0.0
    val wy = if (windLength > 0.0) windTo.y / windLength else 0.0

    var weighted = 0.0
    var weights = 0.0
    var open = 0

    for (ray in reaches.indices) {
      val angle = 2.0 * PI * ray / reaches.size
      val dx = cos(angle)
      val dy = sin(angle)

      // A ray pointing into the wind is a ray the waves come down. The floor keeps a sheltered bearing
      // contributing something rather than nothing, because a shore is never lit from one side only.
      val weight = 0.35 + 0.65 * max(0.0, -(dx * wx + dy * wy))
      val reach = reaches[ray]

      // Square root because wave height in the fetch-limited regime goes roughly as the root of the fetch. The
      // shape is right; the constant it is normalised against is a guess.
      weighted += weight * sqrt(reach / params.fetchReach)
      weights += weight
      if (reach >= params.fetchReach) open++
    }

    val mean = if (weights > 0.0) weighted / weights else 0.0
    val openness = open.toDouble() / reaches.size

    // Openness is what separates a fjord head from a headland: one long reach out of eight is not an exposed
    // shore, however long it is.
    return (mean * (0.5 + 0.5 * openness)).coerceIn(0.0, 1.0)
  }

  private fun march(cx: Int, cy: Int): DoubleArray {
    val reaches = DoubleArray(params.fetchRays)

    for (ray in 0 until params.fetchRays) {
      val angle = 2.0 * PI * ray / params.fetchRays
      val dx = cos(angle)
      val dy = sin(angle)

      var steps = 0
      while (steps < params.fetchReach) {
        val nx = (cx + dx * (steps + 1)).toInt()
        val ny = (cy + dy * (steps + 1)).toInt()

        if (nx !in 0 until width || ny !in 0 until height) break
        if (!ocean[ny * width + nx]) break
        steps++
      }

      reaches[ray] = steps.toDouble()
    }

    return reaches
  }
}
