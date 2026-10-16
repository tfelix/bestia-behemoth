package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WornGearSystemTest {

  private val world: EcsWorld = testWorld(systems = listOf(WornGearSystem()))

  @Test
  fun `worn gear follows equip and unequip`() {
    val entity = createWearer()
    val equipment = world.get(entity, Equipment::class)!!

    equipment.equip(EquipmentSlot.HEAD_UPPER, Equipment.EquippedItem(itemId = IRON_HELMET, uniqueId = 7))
    world.tick(0.1f)
    assertEquals(mapOf(EquipmentSlot.HEAD_UPPER to IRON_HELMET), wornGear(entity).itemIds)

    equipment.unequip(EquipmentSlot.HEAD_UPPER)
    world.tick(0.1f)
    assertEquals(emptyMap<EquipmentSlot, Long>(), wornGear(entity).itemIds)
  }

  /** It runs every tick for every master, so an unchanged outfit must not be resent to everyone in range. */
  @Test
  fun `unchanged gear is not resent`() {
    val entity = createWearer()
    world.get(entity, Equipment::class)!!
      .equip(EquipmentSlot.HEAD_UPPER, Equipment.EquippedItem(itemId = IRON_HELMET, uniqueId = 7))
    world.tick(0.1f)
    assertTrue(wornGear(entity).isDirty())
    wornGear(entity).clearDirty()

    world.tick(0.1f)

    assertFalse(wornGear(entity).isDirty())
  }

  private fun createWearer(): EntityId {
    return world.createEntity { id ->
      add(id, Equipment(availableSlotMask = EquipmentSlots.ALL))
      add(id, WornGear())
    }
  }

  private fun wornGear(entity: EntityId): WornGear {
    return world.get(entity, WornGear::class)!!
  }

  companion object {
    private const val IRON_HELMET = 69L
  }
}
