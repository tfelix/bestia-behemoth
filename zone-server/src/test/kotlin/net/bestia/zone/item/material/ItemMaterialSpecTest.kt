package net.bestia.zone.item.material

import net.bestia.zone.battle.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ItemMaterialSpecTest {

  private val iron = ItemMaterialSpec(
    material = ItemMaterial.METAL,
    integrity = 400,
    hardness = 15,
    elementMultipliers = mapOf(Element.FIRE to 0),
  )

  @Test
  fun `a hit at the hardness does nothing`() {
    assertEquals(0, iron.damageFrom(15, Element.NORMAL))
  }

  @Test
  fun `hardness comes off a heavier hit rather than gating it`() {
    assertEquals(1, iron.damageFrom(16, Element.NORMAL))
  }

  @Test
  fun `an element that is not listed lands in full`() {
    assertEquals(100, iron.multiplierPercent(Element.WIND))
  }

  @Test
  fun `the element is weighed before the hardness comes off`() {
    val paper = ItemMaterialSpec(ItemMaterial.PAPER, integrity = 10, hardness = 1, mapOf(Element.FIRE to 300))

    assertEquals(29, paper.damageFrom(10, Element.FIRE))
  }
}
