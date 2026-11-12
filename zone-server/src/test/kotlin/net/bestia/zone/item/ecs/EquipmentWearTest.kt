package net.bestia.zone.item.ecs

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EquipmentWearTest {

  private val inventoryService = mockk<InventoryService>(relaxed = true)
  private val asyncJobExecutor = mockk<AsyncJobExecutor> {
    every { submit(any<Long>(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }
  private val wear = EquipmentWear(
    EquipmentChanges(ItemTemplateRegistry(mockk<ItemRepository> { every { findAll() } returns emptyList() })),
    inventoryService,
    asyncJobExecutor
  )
  private val world: EcsWorld = testWorld()

  @Test
  fun `a worn weapon stays in hand`() {
    val wielder = wielder(durability = 10)

    wear.wearWeapon(world, wielder, 3)

    assertEquals(7, weapon(wielder)?.durability)
    verify { inventoryService.recordWear(SWORD_ID, 7) }
    verify(exactly = 0) { inventoryService.unequip(any(), any(), any()) }
  }

  @Test
  fun `a weapon worn to nothing falls out of the hand`() {
    val wielder = wielder(durability = 2)

    wear.wearWeapon(world, wielder, 3)

    assertNull(weapon(wielder))
    val inBag = world.get(wielder, Inventory::class)!!.byPlayerItemId(SWORD_ID)!!
    assertFalse(inBag.equipped)
    assertEquals(0, inBag.durability)
    verify { inventoryService.recordWear(SWORD_ID, 0) }
    verify { inventoryService.unequip(MASTER_ID, null, EquipmentSlot.RIGHT_HAND) }
  }

  @Test
  fun `a coated weapon wears less, and not at all at full coating`() {
    val lightlyCoated = wielder(durability = 50).also { coat(it, level = 1) }
    val fullyCoated = wielder(durability = 50).also { coat(it, level = 3) }

    wear.wearWeapon(world, lightlyCoated, 10)
    wear.wearWeapon(world, fullyCoated, 10)

    assertEquals(42, weapon(lightlyCoated)?.durability)
    assertEquals(50, weapon(fullyCoated)?.durability)
  }

  private fun coat(id: EntityId, level: Int) {
    world.add(id, StatusEffects(mutableListOf(ActiveStatusEffect(StatusEffectId.EQUIPMENT_COATING.id, level, 600f))))
  }

  private fun wielder(durability: Int): EntityId {
    return world.createEntity { id ->
      add(id, Master(masterId = MASTER_ID))
      add(id, Inventory(mutableListOf(
        Inventory.Item(
          itemId = SWORD_ITEM, amount = 1, uniqueId = SWORD_ID, stackable = false, equipped = true,
          durability = durability, maxDurability = MAX_DURABILITY
        )
      )))
      add(id, Equipment(availableSlotMask = EquipmentSlots.ALL).apply {
        equip(
          EquipmentSlot.RIGHT_HAND,
          Equipment.EquippedItem(SWORD_ITEM, SWORD_ID, durability = durability, maxDurability = MAX_DURABILITY)
        )
      })
    }
  }

  private fun weapon(id: EntityId): Equipment.EquippedItem? {
    return world.get(id, Equipment::class)?.get(EquipmentSlot.RIGHT_HAND)
  }

  private companion object {
    const val MASTER_ID = 5L
    const val SWORD_ITEM = 40L
    const val SWORD_ID = 900L
    const val MAX_DURABILITY = 100
  }
}
