package net.bestia.worldgen.climate

import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The surface wind turns, and turning it did not move anything else.
 *
 * Before the veer, `windAt` returned `Winds.directionAt(latitude)` and nothing else - a pure function of
 * latitude, so a region had exactly one wind bearing for the life of the world. Nothing in the model minded,
 * which is why it survived: the defect is only visible to something that *reads* the bearing, and until a
 * downwind-biased grass fire wanted it, nothing did.
 *
 * So the first test here is the one that would have failed, and the last two are the guards on the two things
 * the veer must not disturb.
 */
class WindDirectionTest {

  private val world: GeneratedWorld by lazy {
    StandardWorld.build(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
  }

  private val model: WeatherModel by lazy { WeatherModel.of(world) }

  private val temperature: LocalTemperature by lazy {
    LocalTemperature.from(world.world.layers, world.config)
      ?: error("no climate layers on this world")
  }

  /** The bearing at one region on one day, through the same path the runtime takes. */
  private fun bearingAt(region: WeatherRegion, day: Double): Double {
    val air = temperature.at(
      region.centre.x, region.centre.y, region.meanElevation, 0.4, 0.5
    ).airCelsius
    return model.at(region, day, air).windDirection
  }

  private fun landRegions() = model.regions.regions.filter { it.landShare > 0.5 }

  /** Signed smallest angle from [from] to [to], so a bearing crossing the wrap does not read as a huge swing. */
  private fun delta(from: Double, to: Double): Double {
    var d = to - from
    while (d > Math.PI) d -= 2.0 * Math.PI
    while (d < -Math.PI) d += 2.0 * Math.PI
    return d
  }

  @Test
  fun `a region's wind bearing changes from day to day`() {
    val region = landRegions().first()

    val bearings = (0 until 5).map { bearingAt(region, it.toDouble()) }
    val swings = bearings.zipWithNext { a, b -> abs(delta(a, b)) }

    // Not "any two differ": floating-point noise would satisfy that while the wind stood still. A tenth of a
    // radian is about six degrees, which is a change a player could see in the rain and the cloud drift.
    assertTrue(
      swings.any { it > 0.1 },
      "wind bearing over five days moved at most ${"%.4f".format(swings.max())} rad; " +
          "the veer is not reaching the surface wind"
    )
  }

  @Test
  fun `the veer stays inside its configured bound`() {
    val bound = world.params.weather.windVeerRadians
    val regions = landRegions()

    for (region in regions) {
      val prevailing = Winds.directionAt(region.latitude)
      val expected = atan2(prevailing.y, prevailing.x)

      for (step in 0 until 40) {
        val day = step * 0.25
        val off = abs(delta(expected, bearingAt(region, day)))
        assertTrue(
          off <= bound + 1e-9,
          "region ${region.index} on day $day is ${"%.3f".format(off)} rad off its prevailing bearing, " +
              "past the ${"%.3f".format(bound)} rad bound"
        )
      }
    }
  }

  /**
   * The veer is centred on zero, so it must add no net rotation.
   *
   * A veer drawn from `[0, 1)` rather than `[-0.5, 0.5)` would still pass both tests above while rotating
   * every prevailing wind in the world by half its bound - which would move the trade winds off the latitudes
   * that define them, and nothing else in the model would notice.
   *
   * Averaged as unit **vectors**, not as angles: the mean of bearings either side of the wrap is meaningless.
   */
  @Test
  fun `a year of veering averages back to the latitude's prevailing bearing`() {
    for (region in landRegions()) {
      val prevailing = Winds.directionAt(region.latitude)
      val expected = atan2(prevailing.y, prevailing.x)

      var sumX = 0.0
      var sumY = 0.0
      val days = 120
      for (step in 0 until days) {
        val bearing = bearingAt(region, step * 1.0)
        sumX += cos(bearing)
        sumY += sin(bearing)
      }

      val mean = atan2(sumY / days, sumX / days)
      val off = abs(delta(expected, mean))

      // Generous: 120 samples of a smooth field is a small sample of a slow channel, so the point is that the
      // veer is centred, not that it integrates to zero exactly.
      assertTrue(
        off < 0.25,
        "region ${region.index} averages ${"%.3f".format(off)} rad off its prevailing bearing over $days " +
            "days; the veer is biased rather than centred"
      )
    }
  }
}
