package net.bestia.zone.item.material

import net.bestia.zone.battle.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Moves the boot-time refusal of an undescribed material into the commit that added the constant. */
class ItemMaterialRegistryTest {

  private val registry = ItemMaterialRegistry().also { it.load() }

  @Test
  fun `every material is described`() {
    for (material in ItemMaterial.entries) {
      assertTrue(registry.of(material).integrity > 0, "$material has no integrity")
    }
  }

  @Test
  fun `fire burns paper and spares metal`() {
    assertEquals(300, registry.of(ItemMaterial.PAPER).multiplierPercent(Element.FIRE))
    assertEquals(0, registry.of(ItemMaterial.METAL).damageFrom(1000, Element.FIRE))
  }
}
