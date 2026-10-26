package net.bestia.zone.crafting.discovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CraftVectorTest {

  private val herb = CraftVector(mapOf(CraftAxis.STATE to -1.0, CraftAxis.VITALITY to 0.9))
  private val stone = CraftVector(mapOf(CraftAxis.STATE to 1.0, CraftAxis.HEFT to 0.6))

  /** Two herbs and a stone weigh the herb twice, whatever the batch size. */
  @Test
  fun `a mix is the amount-weighted mean of its materials`() {
    val mix = CraftVector.mixOf(listOf(herb to 2, stone to 1))

    assertEquals(0.0, mix.distanceTo(CraftVector.mixOf(listOf(herb to 20, stone to 10))), 1e-9)
    assertEquals(-1.0 / 3, mix[CraftAxis.STATE], 1e-9)
    assertEquals(0.6, mix[CraftAxis.VITALITY], 1e-9)
    assertEquals(0.2, mix[CraftAxis.HEFT], 1e-9)
    assertEquals(0.0, mix[CraftAxis.EDGE], 1e-9)
  }

  @Test
  fun `an axis outside minus one to one is refused`() {
    assertThrows<IllegalArgumentException> { CraftVector(mapOf(CraftAxis.EDGE to 1.5)) }
  }

  @Test
  fun `distance counts every axis`() {
    assertEquals(0.0, herb.distanceTo(herb), 1e-9)
    assertEquals(Math.sqrt(4.0 + 0.81 + 0.36), herb.distanceTo(stone), 1e-9)
  }
}
