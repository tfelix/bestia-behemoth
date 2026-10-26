package net.bestia.zone.crafting.discovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MaterialAxesSMSGTest {

  /** The client reads the axes by position, so the order is the contract. */
  @Test
  fun `a material's axes go out in CraftAxis order`() {
    val herb = CraftVector(mapOf(CraftAxis.STATE to -1.0, CraftAxis.VITALITY to 0.5))

    val sent = MaterialAxesSMSG(mapOf(11L to herb)).toBnetEnvelope().materialAxes.materialsList.single()

    assertEquals(11, sent.itemId)
    assertEquals(listOf(-1f, 0f, 0f, 0f, 0.5f, 0f, 0f), sent.axesList)
  }
}
