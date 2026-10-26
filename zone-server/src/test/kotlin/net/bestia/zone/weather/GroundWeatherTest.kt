package net.bestia.zone.weather

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.climate.Temperature
import net.bestia.worldgen.climate.WeatherKind
import net.bestia.worldgen.climate.WeatherState
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.world.time.BestiaDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GroundWeatherTest {

  private var nowHour = 1_000L

  private val clock = mockk<BestiaClock> {
    every { now() } answers { dateTime(nowHour * SECONDS_PER_HOUR) }
    every { atAbsoluteSecond(any()) } answers { dateTime(firstArg()) }
  }

  private val worldService = mockk<WorldService> {
    every { generated.params.weather.rainRateMmPerDay } returns 48.0
  }

  /** Snows on even hours, and the air swings between -8 and +8 degrees over a day. */
  private val weatherService = mockk<WeatherService> {
    every { regionOf(any(), any()) } returns REGION
    every { regionCentreVoxel(REGION) } returns (0L to 0L)
    every { lapseRate } returns 6.2
    every { at(any(), any(), any(), any()) } answers {
      val hour = arg<BestiaDateTime>(3).absoluteSecond / SECONDS_PER_HOUR
      val kind = if (hour % 2 == 0L) WeatherKind.SNOW else WeatherKind.CLOUDY
      val state = WeatherState(kind, intensity = 0.5, cloudCover = 0.8, windSpeed = 3.0, windDirection = 0.0)
      WeatherAt(REGION, state, Temperature(-8.0 + 16.0 * ((hour % 24) / 23.0)))
    }
  }

  private fun dateTime(second: Long): BestiaDateTime {
    return mockk { every { absoluteSecond } returns second }
  }

  private fun ground() = GroundWeather(weatherService, worldService, clock, GroundWeatherConfig())

  @Test
  fun `an answer carried forward hour by hour equals one replayed cold`() {
    val carried = ground()
    for (hour in 1_000L..1_010L) {
      nowHour = hour
      carried.at(5L, 5L, 200.0)
    }

    val cold = ground()

    assertEquals(cold.at(5L, 5L, 200.0), carried.at(5L, 5L, 200.0))
  }

  @Test
  fun `the weather is evaluated once per region per hour`() {
    val ground = ground()

    ground.at(5L, 5L, 200.0)
    ground.at(900L, 900L, 1_500.0)
    nowHour += 1
    ground.at(5L, 5L, 200.0)

    verify(exactly = GroundWeatherConfig().replayHours + 1) { weatherService.at(any(), any(), any(), any()) }
  }

  private companion object {
    const val REGION = 3
    const val SECONDS_PER_HOUR = 3_600L
  }
}
