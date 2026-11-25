package net.bestia.zone.weather

import net.bestia.zone.world.mana.ManaField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LiveManaReadingTest {

  private val field = ManaField()

  /** Two regions on a 10 km world, split down the middle. */
  private val reading = LiveManaReading(
    regionCount = 2,
    regionAt = { x, _ -> if (x < 5_000.0) WEST else EAST },
    width = 10_000.0,
    height = 10_000.0,
    startDensityAt = { _, _ -> START },
  )

  @Test
  fun `an untouched world reads its start density`() {
    reading.refresh(field)

    assertEquals(START, reading.peakOf(WEST), 1e-9)
    assertEquals(START, reading.peakOf(EAST), 1e-9)
  }

  @Test
  fun `a hot spot over a twentieth of a region is enough to read it hot`() {
    field.cellsWithin(1_000.0, 1_000.0, 1_200.0).forEach { field.setDelta(it, 0.5) }

    reading.refresh(field)

    assertEquals(START + 0.5, reading.peakOf(WEST), 0.05)
    assertEquals(START, reading.peakOf(EAST), 1e-9)
  }

  @Test
  fun `the reading stays frozen until the next refresh`() {
    reading.refresh(field)
    field.cellsWithin(1_000.0, 1_000.0, 1_200.0).forEach { field.setDelta(it, 0.5) }

    assertEquals(START, reading.peakOf(WEST), 1e-9)
  }

  private companion object {
    const val WEST = 0
    const val EAST = 1
    const val START = 0.13
  }
}
