package net.bestia.zone.item.ecs

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.world.time.BestiaDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WornGearSystemTest {

  private var now = NOON
  private val clock = mockk<BestiaClock> { every { now() } answers { now } }
  private val world: EcsWorld = testWorld(systems = listOf(WornGearSystem(clock)))

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

  @Test
  fun `a worn light is shown lit in the dark only`() {
    val entity = createWearer()
    world.get(entity, Equipment::class)!!
      .equip(EquipmentSlot.LIGHT_SOURCE, Equipment.EquippedItem(itemId = TORCH, uniqueId = 8))
    world.tick(0.1f)
    assertFalse(wornGear(entity).lightLit)

    now = MIDNIGHT
    world.tick(0.1f)

    assertTrue(wornGear(entity).lightLit)
    assertTrue(wornGear(entity).isDirty())
  }

  @Test
  fun `no light is lit without a light worn`() {
    now = MIDNIGHT
    val entity = createWearer()

    world.tick(0.1f)

    assertFalse(wornGear(entity).lightLit)
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
    private const val IRON_HELMET = 71L
    private const val TORCH = 35L
    private val NOON = BestiaDateTime(year = 1, month = 1, day = 1, hour = 12, minute = 0, second = 0)
    private val MIDNIGHT = BestiaDateTime(year = 1, month = 1, day = 1, hour = 0, minute = 0, second = 0)
  }
}
