package net.bestia.worldgen.climate

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `WeatherState.dryness`: the air mass standing in for the ground.
 *
 * The property that matters to a consumer is the one this model had no way to express before - **a memory
 * longer than the front standing over you now**. So the load-bearing test here is the last one, which is the
 * one a dryness computed from `intensity` alone would fail.
 */
class WeatherDrynessTest {

  private val world: GeneratedWorld by lazy {
    StandardWorld.build(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
  }

  private val model: WeatherModel by lazy { WeatherModel.of(world) }

  private val temperature: LocalTemperature by lazy {
    LocalTemperature.from(world.world.layers, world.config)
      ?: error("no climate layers on this world")
  }

  private fun stateAt(region: WeatherRegion, day: Double): WeatherState {
    val air = temperature.at(
      region.centre.x, region.centre.y, region.meanElevation, 0.4, 0.5
    ).airCelsius
    return model.at(region, day, air)
  }

  private fun landRegions() = model.regions.regions.filter { it.landShare > 0.5 }

  /** Every land region over a year, at a quarter-day step. */
  private fun census(): List<WeatherState> {
    val out = ArrayList<WeatherState>()
    for (region in landRegions()) {
      for (step in 0 until 480) out.add(stateAt(region, step * 0.25))
    }
    return out
  }

  @Test
  fun `dryness is always a share`() {
    for (state in census()) {
      assertTrue(state.dryness in 0.0..1.0, "dryness ${state.dryness} is not a share")
    }
  }

  @Test
  fun `ground under a downpour is not dry`() {
    val raining = census().filter { it.intensity > 0.9 }

    assertTrue(raining.isNotEmpty(), "no sample rained hard enough to test the veto; the census is too small")
    for (state in raining) {
      assertTrue(
        state.dryness < 0.1,
        "dryness ${"%.3f".format(state.dryness)} under intensity ${"%.3f".format(state.intensity)}; " +
            "the current front is not vetoing"
      )
    }
  }

  /**
   * The air-mass channel is squashed uniform by `FBM_CDF`, so dryness inherits that spread once the rain veto
   * is out of the way. If it clusters instead, the formula has lost the property every threshold in
   * `WeatherParams` relies on - and a fuel model reading "dryness above 0.7" would mean something other than
   * "the driest thirty percent".
   */
  @Test
  fun `dryness spreads across its range rather than clustering`() {
    val dry = census().filter { it.intensity == 0.0 }.map { it.dryness }.sorted()

    assertTrue(dry.size > 1_000, "only ${dry.size} dry-sky samples; the spread would mean nothing")

    fun quantile(share: Double) = dry[(share * (dry.size - 1)).toInt()]

    for (share in listOf(0.25, 0.50, 0.75)) {
      val at = quantile(share)
      assertTrue(
        abs(at - share) < 0.2,
        "quantile $share sits at ${"%.3f".format(at)}; dryness is clustered, not spread"
      )
    }
  }

  /**
   * **The test the whole field exists for.**
   *
   * Dryness must still separate regions on a day when *nobody* is being rained on, because that is exactly the
   * case a consumer computing it from `intensity` cannot tell apart: to that version, every dry sky in the
   * world is equally dry, and a fire lit a minute after a downpour spreads like one lit in a drought.
   */
  @Test
  fun `two regions with the same clear sky can still differ in dryness`() {
    val clear = landRegions()
      .map { it to stateAt(it, 3.0) }
      .filter { (_, state) -> state.intensity == 0.0 }

    assertTrue(clear.size >= 4, "only ${clear.size} regions had a dry sky on day 3; nothing to compare")

    val spread = clear.maxOf { it.second.dryness } - clear.minOf { it.second.dryness }
    assertTrue(
      spread > 0.2,
      "every dry-sky region sat within ${"%.3f".format(spread)} of the others; dryness carries no memory " +
          "beyond the current front, which is the one thing it is for"
    )
  }
}
