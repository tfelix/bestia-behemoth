package net.bestia.zone.postal.parcel

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FreightTest {

  @Test
  fun `the fee is ten gold per km and kg, rounded up`() {
    assertEquals(400L, Freight.fee(roadKm = 20.0, weightUnits = 2 * Freight.UNITS_PER_KG))
    assertEquals(16L, Freight.fee(roadKm = 1.55, weightUnits = Freight.UNITS_PER_KG))
  }

  @Test
  fun `a parcel costs at least one gold`() {
    assertEquals(1L, Freight.fee(roadKm = 0.0, weightUnits = 50))
    assertEquals(1L, Freight.fee(roadKm = 3.0, weightUnits = 0))
  }
}
