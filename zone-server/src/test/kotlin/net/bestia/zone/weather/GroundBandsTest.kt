package net.bestia.zone.weather

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GroundBandsTest {

  private val config = GroundWeatherConfig()

  private fun hours(count: Int, precipitationMm: Double, seaLevelCelsius: Double): List<HourSample> {
    return List(count) { HourSample(precipitationMm, seaLevelCelsius) }
  }

  private fun replay(samples: List<HourSample>): GroundBands {
    return GroundBands.replay(samples, LAPSE_RATE, config)
  }

  @Test
  fun `snow lies above the freezing level and rain wets the ground below it`() {
    // Three degrees at sea level freezes from about 480 m.
    val bands = replay(hours(10, precipitationMm = 2.0, seaLevelCelsius = 3.0))

    val valley = bands.coverAt(0.0)
    val snowLine = requireNotNull(valley.snowLine)

    assertTrue(snowLine in 450.0..550.0, "snow line at $snowLine")
    assertTrue(valley.wetness > 0.8, "the valley is only ${valley.wetness} wet after ten hours of rain")
    assertEquals(0.0, bands.coverAt(1_000.0).wetness, 1e-9, "snow is not wet")
  }

  @Test
  fun `a thaw lifts the snow line`() {
    val snowed = hours(10, precipitationMm = 2.0, seaLevelCelsius = -5.0)
    val thawed = snowed + hours(40, precipitationMm = 0.0, seaLevelCelsius = 10.0)

    assertEquals(config.lowestBandMetres, replay(snowed).coverAt(0.0).snowLine)

    // Ten degrees at sea level freezes from about 1610 m, where nothing melts.
    val snowLine = requireNotNull(replay(thawed).coverAt(0.0).snowLine)
    assertTrue(snowLine in 1_000.0..1_650.0, "snow line at $snowLine")
  }

  @Test
  fun `wet ground dries once the rain stops`() {
    val rained = hours(3, precipitationMm = 4.0, seaLevelCelsius = 15.0)
    val dried = rained + hours(12, precipitationMm = 0.0, seaLevelCelsius = 15.0)

    assertTrue(replay(rained).coverAt(0.0).wetness > 0.9)
    assertTrue(replay(dried).coverAt(0.0).wetness < 0.05)
  }

  @Test
  fun `a dry window leaves bare ground`() {
    val cover = replay(hours(24, precipitationMm = 0.0, seaLevelCelsius = -10.0)).coverAt(300.0)

    assertEquals(0.0, cover.wetness, 1e-9)
    assertNull(cover.snowLine)
    assertNull(cover.snowFull)
  }

  @Test
  fun `full cover lies above the patchy snow line`() {
    // More snow higher up only because less of it melted there.
    val samples = hours(10, precipitationMm = 1.0, seaLevelCelsius = -10.0) +
        hours(10, precipitationMm = 0.0, seaLevelCelsius = 6.0)
    val cover = replay(samples).coverAt(0.0)

    val line = requireNotNull(cover.snowLine)
    val full = requireNotNull(cover.snowFull)
    assertTrue(full > line, "full cover at $full is not above the snow line at $line")
  }

  private companion object {
    const val LAPSE_RATE = 6.2
  }
}
