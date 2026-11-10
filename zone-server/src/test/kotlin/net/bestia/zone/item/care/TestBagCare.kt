package net.bestia.zone.item.care

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.script.BandageScript
import net.bestia.zone.item.script.BreadScript
import net.bestia.zone.item.script.CheeseScript
import net.bestia.zone.item.script.ItemScriptExecutionService
import net.bestia.zone.item.script.SmallHealthPotionScript

/** A [BagCare] over the real scripts of a few catalogue items, with the database behind it mocked away. */
object TestBagCare {

  const val BREAD = 30L
  const val CHEESE = 42L
  const val BANDAGE = 34L
  const val POTION = 3L

  private val MATERIALS = mapOf(
    BREAD to ItemMaterial.FOOD,
    CHEESE to ItemMaterial.FOOD,
    BANDAGE to ItemMaterial.CLOTH,
    POTION to ItemMaterial.GLASS,
  )

  fun create(): BagCare {
    val templates = mockk<ItemTemplateRegistry>().also { registry ->
      every { registry.isBound(any()) } returns false
      every { registry.templateOf(any()) } answers {
        val id = firstArg<Long>()
        MATERIALS[id]?.let { material ->
          ItemTemplateRegistry.Template(
            id = id,
            baseLevel = 1,
            weight = 1,
            stackable = true,
            maxDurability = 0,
            material = material,
            type = Item.ItemType.USABLE,
          )
        }
      }
    }
    val scripts = ItemScriptExecutionService(
      listOf(BreadScript(), CheeseScript(), BandageScript(mockk(relaxed = true)), SmallHealthPotionScript(mockk(relaxed = true)))
    )

    return BagCare(templates, scripts, mockk(relaxed = true), mockk(relaxed = true))
  }

  fun bagWith(vararg items: Pair<Long, Int>): Inventory =
    Inventory(items.map { (id, amount) -> Inventory.Item(itemId = id, amount = amount) }.toMutableList())
}
