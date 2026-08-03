package net.bestia.worldgen.climate

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three properties that made an advected noise field the right choice over a Markov chain.
 *
 * Each is asserted here because each is the reason a chain was rejected, and a refactor that quietly replaced
 * the field with something stateful would still produce plausible weather while breaking all three - and
 * `WEATHER_SENSE`, which `skills.yml` already sells to players, silently along with them.
 */
class WeatherForecastTest {

  private val world: GeneratedWorld by lazy {
    StandardWorld.build(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
  }

  private val model: WeatherModel by lazy { WeatherModel.of(world) }

  private val temperature: LocalTemperature by lazy {
    LocalTemperature.from(world.world.layers, world.config) ?: error("no climate layers")
  }

  private fun stateAt(region: WeatherRegion, day: Double): WeatherState {
    val yearProgress = (day % WeatherModel.DAYS_PER_YEAR) / WeatherModel.DAYS_PER_YEAR
    val air = temperature.at(
      region.centre.x, region.centre.y, region.meanElevation, yearProgress, day - Math.floor(day)
    ).airCelsius
    return model.at(region, day, air)
  }

  private fun landRegion() = model.regions.regions.first { it.landShare > 0.5 }

  @Test
  fun `a forecast is the answer, not a distribution`() {
    // The property `WEATHER_SENSE` is sold on. Evaluating a future day now and evaluating it again when it
    // arrives has to give the same thing - which is free for a stateless field and impossible for a chain,
    // whose answer depends on every step it took to get there.
    val region = landRegion()

    for (ahead in listOf(0.5, 1.0, 3.0, 17.0, 121.0)) {
      val foreseen = stateAt(region, 40.0 + ahead)
      val arrived = stateAt(region, 40.0 + ahead)

      assertEquals(foreseen.kind, arrived.kind, "the forecast for day $ahead was not what arrived")
      assertEquals(foreseen.intensity, arrived.intensity, "intensity moved between two identical queries")
    }
  }

  @Test
  fun `any day is reachable without walking to it`() {
    // O(1) random access, the other half of the same argument. A chain would have to iterate from an anchor,
    // and this is the assertion that would fail if somebody introduced one - day 40,000 costs the same as day 1.
    val region = landRegion()

    val early = System.nanoTime()
    stateAt(region, 1.0)
    val earlyCost = System.nanoTime() - early

    val late = System.nanoTime()
    stateAt(region, 40_000.0)
    val lateCost = System.nanoTime() - late

    // Very loose: this is catching an O(t) loop, which would be four orders of magnitude, not a 3x wobble
    // from the JIT.
    assertTrue(
      lateCost < earlyCost * 50 + 1_000_000,
      "day 40000 cost ${lateCost}ns against day 1's ${earlyCost}ns; something is iterating"
    )
  }

  @Test
  fun `the sky does not whiplash`() {
    // Temporal coherence, and it is structural rather than tuned: cloud is a lower threshold on the same
    // channel as rain, so the field must climb through overcast to reach rain and through rain to reach storm.
    // Clear straight to tornado is not improbable here, it is impossible - and this is what says so.
    val region = model.regions.regions.first { it.landShare > 0.5 && it.meanMana < 0.5 }

    var previous = stateAt(region, 0.0).kind
    var whiplashes = 0
    var day = 0.125

    while (day < WeatherModel.DAYS_PER_YEAR) {
      val current = stateAt(region, day).kind

      // A jump from a dry sky straight into an organised storm, with no cloud or rain in between.
      val dry = previous == WeatherKind.CLEAR
      val violent = current == WeatherKind.TORNADO || current == WeatherKind.THUNDERSTORM
      if (dry && violent) whiplashes++

      previous = current
      day += 0.125
    }

    assertEquals(0, whiplashes, "the sky went from clear to a storm $whiplashes times in a year")
  }

  @Test
  fun `a front sweeps across neighbouring regions rather than flickering per region`() {
    // Spatial coherence. Without it the wind direction a player is shown predicts nothing, and each region's
    // weather is an independent die roll - which looks like static from the air.
    val regions = model.regions.regions.filter { it.landShare > 0.3 }
    if (regions.size < 6) return

    // Correlation between a region's cloud cover and its nearest neighbour's, against the same statistic
    // computed against a *random* other region. A field with no spatial structure scores the same for both.
    var neighbourAgreement = 0
    var strangerAgreement = 0
    var samples = 0

    for ((index, region) in regions.withIndex()) {
      val neighbour = regions
        .filter { it !== region }
        .minBy { hypot(it.centre.x - region.centre.x, it.centre.y - region.centre.y) }
      val stranger = regions[(index + regions.size / 2) % regions.size]
      if (stranger === region) continue

      for (step in 0 until 40) {
        val day = step * 0.7
        val here = stateAt(region, day).cloudCover
        if (agree(here, stateAt(neighbour, day).cloudCover)) neighbourAgreement++
        if (agree(here, stateAt(stranger, day).cloudCover)) strangerAgreement++
        samples++
      }
    }

    val neighbourShare = neighbourAgreement.toDouble() / samples
    val strangerShare = strangerAgreement.toDouble() / samples
    println(
      "cloud agreement: nearest neighbour %.3f, distant region %.3f".format(neighbourShare, strangerShare)
    )

    assertTrue(
      neighbourShare > strangerShare * 1.3,
      "a region agrees with its neighbour %.3f of the time and with a distant region %.3f - the field has no spatial structure"
        .format(neighbourShare, strangerShare)
    )
  }

  private fun agree(a: Double, b: Double) = Math.abs(a - b) < 0.15

  private fun hypot(dx: Double, dy: Double) = Math.sqrt(dx * dx + dy * dy)
}
