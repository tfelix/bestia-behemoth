package net.bestia.zone.world.mana

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The potential is a rank, so it is spread evenly over the land by construction (`ManaStageTest` pins that). The
 * share of land in each step at world start therefore follows from the curve alone, without generating a world.
 */
class StartDensityTest {

  private val land = (0 until SAMPLES).map { StartDensity.of((it + 0.5) / SAMPLES) }

  private fun shareFrom(low: Double, high: Double = Double.MAX_VALUE) =
    land.count { it >= low && it < high }.toDouble() / SAMPLES

  @Test
  fun `most land starts calm`() {
    assertEquals(0.82, shareFrom(0.0, STIRRING), 0.01)
  }

  @Test
  fun `only the top of the potential starts restless`() {
    assertEquals(0.07, shareFrom(STIRRING, UNEASY), 0.01)
    assertEquals(0.06, shareFrom(UNEASY, OMINOUS), 0.01)
    assertEquals(0.025, shareFrom(OMINOUS, STORMS), 0.01)
    assertEquals(0.025, shareFrom(STORMS), 0.01)
  }

  @Test
  fun `no land starts ready for an event`() {
    assertEquals(0.0, shareFrom(EVENT))
  }

  private companion object {
    const val SAMPLES = 100_000
    const val STIRRING = 0.35
    const val UNEASY = 0.45
    const val OMINOUS = 0.55
    const val STORMS = 0.60
    const val EVENT = 0.70
  }
}
