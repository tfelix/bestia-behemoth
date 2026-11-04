package net.bestia.worldgen.climate

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds
import kotlin.test.Test
import kotlin.test.assertTrue

class StormWindTest {

  private val world: GeneratedWorld by lazy {
    TestWorlds.get(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
  }

  private val model: WeatherModel by lazy { WeatherModel.of(world) }

  /** Every hour of one year in every land region, warm enough that a wet day can become a thunderstorm. */
  private fun warmYear(): List<WeatherState> {
    val hours = 0 until WeatherModel.DAYS_PER_YEAR.toInt() * 24
    return model.regions.regions
      .filter { it.landShare > 0.5 }
      .flatMap { region -> hours.map { hour -> model.at(region, hour / 24.0, WARM_CELSIUS) } }
  }

  @Test
  fun `a thunderstorm never arrives on a breeze`() {
    val year = warmYear()
    val storms = year.filter { it.kind in WeatherModel.CONVECTIVE_STORMS }

    assertTrue(storms.isNotEmpty(), "no thunderstorm in a warm year; the test proves nothing")
    val calmest = storms.minOf { it.windSpeed }
    assertTrue(calmest >= WeatherModel.STORM_WIND, "a storm came with only $calmest m/s of wind")
  }

  @Test
  fun `calm days keep their own wind`() {
    val calm = warmYear().filter { it.kind !in WeatherModel.CONVECTIVE_STORMS }

    assertTrue(calm.any { it.windSpeed < WeatherModel.STORM_WIND }, "every day is as windy as a storm")
  }

  @Test
  fun `an ordinary hour blows a breeze, not a gale`() {
    val winds = warmYear().filter { it.kind !in WeatherModel.CONVECTIVE_STORMS }.map { it.windSpeed }.sorted()

    val median = winds[winds.size / 2]
    assertTrue(median in 3.0..7.0, "the median hour blows $median m/s")
    val galeShare = winds.count { it >= WeatherModel.STORM_WIND }.toDouble() / winds.size
    assertTrue(galeShare < 0.01, "a near gale blows in %.1f%% of ordinary hours".format(galeShare * 100))
  }

  private companion object {
    private const val WARM_CELSIUS = 22.0
  }
}
