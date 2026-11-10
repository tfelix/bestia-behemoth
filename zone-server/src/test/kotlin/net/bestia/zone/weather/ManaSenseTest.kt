package net.bestia.zone.weather

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.mana.ManaConcentrationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ManaSenseTest {

  /** Mana rises to the east and falls to the west: 0.5 at the origin, 0.1 more every 500 m. */
  private val sut = ManaSense(mockk<ManaConcentrationService> {
    every { manaAt(any()) } answers { 0.5 + firstArg<Vec3L>().x / 5000.0 }
  })

  @Test
  fun `without the skill there is no reading`() {
    assertNull(sut.read(ORIGIN, weatherSenseLevel = 0))
  }

  @Test
  fun `level 1 reads only the spot`() {
    assertEquals(ManaReading(0.5, null, null), sut.read(ORIGIN, weatherSenseLevel = 1))
  }

  @Test
  fun `level 2 points east to the stronger and west to the weaker field`() {
    val reading = sut.read(ORIGIN, weatherSenseLevel = 2)!!

    assertEquals(0.0, reading.stronger!!.directionRadians, 1e-9)
    assertEquals(500.0, reading.stronger!!.distanceMetres)
    assertEquals(Math.PI, reading.weaker!!.directionRadians, 1e-9)
  }

  @Test
  fun `a field farther away than the range is not found`() {
    val flat = ManaSense(mockk<ManaConcentrationService> {
      every { manaAt(any()) } answers { if (firstArg<Vec3L>().x > 1500) 0.9 else 0.5 }
    })

    assertNull(flat.read(ORIGIN, weatherSenseLevel = 2)!!.stronger)
    assertEquals(1750.0, flat.read(ORIGIN, weatherSenseLevel = 3)!!.stronger!!.distanceMetres)
  }

  private companion object {
    val ORIGIN = Vec3L(0, 0, 0)
  }
}
