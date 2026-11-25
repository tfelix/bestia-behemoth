package net.bestia.zone.master.ecs

import io.mockk.mockk
import net.bestia.zone.casting.GearOperations
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.EquipmentChanges
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.itemTemplatesOf
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeathWearSystemTest {

  private val world = testWorld()
  private val templates = itemTemplatesOf()
  private val gear = GearOperations(EquipmentChanges(templates), mockk(relaxed = true), mockk(relaxed = true), templates)
  private val sut = DeathWearSystem(WorldRulesConfig(tickRate = 10), gear)

  @Test
  fun `a death takes a tenth of the maximum off every worn piece`() {
    val master = masterWearing(
      EquipmentSlot.HEAD_UPPER to piece(1L, durability = 50, max = 50),
      EquipmentSlot.FOOTGEAR to piece(2L, durability = 100, max = 100),
      EquipmentSlot.RIGHT_HAND to piece(3L, durability = 80, max = 200),
    )

    dies(master)

    val equipment = world.get(master, Equipment::class)!!
    assertEquals(45, equipment.get(EquipmentSlot.HEAD_UPPER)!!.durability)
    assertEquals(90, equipment.get(EquipmentSlot.FOOTGEAR)!!.durability)
    assertEquals(60, equipment.get(EquipmentSlot.RIGHT_HAND)!!.durability)
  }

  @Test
  fun `the gear wears once however long the body lies there`() {
    val master = masterWearing(EquipmentSlot.ARMOR to piece(1L, durability = 100, max = 100))

    dies(master)
    sut.update(world, 0.1f)

    assertEquals(90, world.get(master, Equipment::class)!!.get(EquipmentSlot.ARMOR)!!.durability)
  }

  @Test
  fun `a torch keeps its burn time`() {
    val master = masterWearing(EquipmentSlot.LIGHT_SOURCE to piece(1L, durability = 30, max = 60))

    dies(master)

    assertEquals(30, world.get(master, Equipment::class)!!.get(EquipmentSlot.LIGHT_SOURCE)!!.durability)
  }

  @Test
  fun `a piece that breaks comes off into the bag`() {
    val master = masterWearing(EquipmentSlot.GARMENT to piece(1L, durability = 5, max = 100))

    dies(master)

    assertNull(world.get(master, Equipment::class)!!.get(EquipmentSlot.GARMENT))
    assertEquals(0, world.get(master, Inventory::class)!!.byPlayerItemId(1L)!!.durability)
  }

  private fun dies(id: EntityId) {
    world.add(id, Dead())
    sut.update(world, 0.1f)
  }

  private fun piece(uniqueId: Long, durability: Int, max: Int): Equipment.EquippedItem {
    return Equipment.EquippedItem(itemId = 100L + uniqueId, uniqueId = uniqueId, durability = durability, maxDurability = max)
  }

  private fun masterWearing(vararg worn: Pair<EquipmentSlot, Equipment.EquippedItem>): EntityId {
    return world.createEntity { id ->
      val equipment = Equipment(EquipmentSlots.ALL)
      val inventory = Inventory(
        worn.map { (_, item) ->
          Inventory.Item(itemId = item.itemId, amount = 1, uniqueId = item.uniqueId, stackable = false, equipped = true)
        }.toMutableList()
      )
      worn.forEach { (slot, item) -> equipment.equip(slot, item) }
      add(id, equipment)
      add(id, inventory)
      add(id, Master(masterId = 7L))
      add(id, Account(1L))
    }
  }
}
