package net.bestia.zone.world.mana

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManaConcentrationTest {

  @Test
  fun `an ordinary field changes nothing`() {
    assertEquals(0, ManaConcentration.place(0.4))
    assertEquals(0, ManaConcentration.place(0.5))
    assertEquals(0, ManaConcentration.place(0.6))
    assertEquals(1.0, ManaConcentration.factor(0.5))
  }

  @Test
  fun `an empty field weakens magic and a full one strengthens it`() {
    assertEquals(-20, ManaConcentration.place(0.0))
    assertEquals(20, ManaConcentration.place(1.0))
    assertEquals(0.5, ManaConcentration.factor(0.0))
    assertEquals(1.5, ManaConcentration.factor(1.0))
  }

  @Test
  fun `a field beyond full keeps rising but tapers below 40`() {
    val high = ManaConcentration.place(1.5)
    val extreme = ManaConcentration.place(10.0)

    assertTrue(high in 21..39)
    assertTrue(extreme in high..39)
  }
}
