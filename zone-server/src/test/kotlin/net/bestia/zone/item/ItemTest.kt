package net.bestia.zone.item

import net.bestia.zone.item.equip.ArmorType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ItemTest {

  @Test
  fun `USABLE item without a script throws`() {
    assertThrows<IllegalArgumentException> {
      Item(
        id = 1,
        identifier = "apple",
        weight = 1,
        type = Item.ItemType.USABLE,
        script = null
      )
    }
  }

  @Test
  fun `USABLE item with a script is valid`() {
    val item = Item(
      id = 1,
      identifier = "apple",
      weight = 1,
      type = Item.ItemType.USABLE,
      script = "AppleScript"
    )

    assertEquals("AppleScript", item.script)
  }

  @Test
  fun `ETC item without a script is valid`() {
    val item = Item(
      id = 2,
      identifier = "jelly",
      weight = 1,
      type = Item.ItemType.ETC
    )

    assertEquals(null, item.script)
  }

  @Test
  fun `description is optional`() {
    val item = Item(
      id = 3,
      identifier = "small_health_potion",
      weight = 1,
      type = Item.ItemType.USABLE,
      script = "SmallHealthPotionScript",
      description = "Heals a small amount of health."
    )

    assertEquals("Heals a small amount of health.", item.description)
  }

  @Test
  fun `an armor type on an item that is not equipment throws`() {
    assertThrows<IllegalArgumentException> {
      Item(
        id = 2,
        identifier = "jelly",
        weight = 1,
        type = Item.ItemType.ETC,
        armorType = ArmorType.CLOTH
      )
    }
  }
}
