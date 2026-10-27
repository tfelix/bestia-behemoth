package net.bestia.zone.item.ecs

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.world.time.BestiaDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LightSourceSystemTest {

  private var now = MIDNIGHT
  private val clock = mockk<BestiaClock> { every { now() } answers { now } }
  private val inventoryService = mockk<InventoryService>(relaxed = true)
  private val outMessageProcessor = mockk<OutMessageProcessor>(relaxed = true)

  /** Runs each job at once, so the database side can be checked without waiting on a thread. */
  private val asyncJobExecutor = mockk<AsyncJobExecutor> {
    every { submit(any<Long>(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }

  private val world: EcsWorld = testWorld(
    systems = listOf(
      LightSourceSystem(
        clock,
        EquipmentChanges(ItemTemplateRegistry(mockk<ItemRepository> { every { findAll() } returns listOf(TORCH) })),
        inventoryService,
        asyncJobExecutor,
        outMessageProcessor
      )
    )
  )

  @Test
  fun `a torch does not burn by day`() {
    now = NOON
    val master = createMaster(wornDurability = 100)

    burnOnce()

    assertEquals(100, light(master)?.durability)
    verify(exactly = 0) { inventoryService.recordWear(any(), any()) }
  }

  @Test
  fun `a torch burns one point per run in the dark and the point is saved`() {
    val master = createMaster(wornDurability = 100)

    burnOnce()

    assertEquals(99, light(master)?.durability)
    verify { inventoryService.recordWear(WORN_ID, 99) }
  }

  @Test
  fun `a burnt out torch is replaced by the stub closest to its end before the pile`() {
    val master = createMaster(
      wornDurability = 1,
      bag = listOf(
        torchCopy(uniqueId = 20L, durability = 200),
        torchCopy(uniqueId = 21L, durability = 50),
        Inventory.Item(itemId = TORCH.id, amount = 3, weight = 30)
      )
    )

    burnOnce()

    assertEquals(21L, light(master)?.uniqueId)
    assertNull(inventory(master).byPlayerItemId(WORN_ID))
    verify { inventoryService.burnOut(MASTER_ID, null, EquipmentSlot.LIGHT_SOURCE) }
    verify { inventoryService.equip(MASTER_ID, null, TORCH.id, 21L, EquipmentSlot.LIGHT_SOURCE) }
  }

  @Test
  fun `with no stub left the next torch comes off the pile`() {
    val master = createMaster(wornDurability = 1, bag = listOf(Inventory.Item(itemId = TORCH.id, amount = 3, weight = 30)))

    burnOnce()

    val relit = light(master)!!
    assertEquals(TORCH.maxDurability, relit.durability)
    assertEquals(2, inventory(master).getItems().single { it.isStackable }.amount)
    verify { inventoryService.equipFromPile(MASTER_ID, null, TORCH.id, null, relit.uniqueId, EquipmentSlot.LIGHT_SOURCE) }
  }

  @Test
  fun `the last torch empties the slot and tells the player`() {
    val master = createMaster(wornDurability = 1)

    burnOnce()

    assertNull(light(master))
    verify { outMessageProcessor.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OpError.LIGHT_SOURCE_BURNT_OUT)) }
  }

  @Test
  fun `a bestia's torch is written to its own bag, keyed by its master`() {
    val bestia = world.createEntity { id ->
      add(id, OwnedBestia(masterId = MASTER_ID, playerBestiaId = BESTIA_ID))
      addWearer(id, wornDurability = 100, bag = emptyList())
    }

    burnOnce()

    assertEquals(99, light(bestia)?.durability)
    verify { asyncJobExecutor.submit(MASTER_ID, any()) }
  }

  private fun burnOnce() = world.tick(10f)

  private fun createMaster(wornDurability: Int, bag: List<Inventory.Item> = emptyList()): EntityId {
    return world.createEntity { id ->
      add(id, Master(masterId = MASTER_ID))
      add(id, Account(accountId = ACCOUNT_ID))
      addWearer(id, wornDurability, bag)
    }
  }

  private fun net.bestia.zone.ecs.core.World.addWearer(id: EntityId, wornDurability: Int, bag: List<Inventory.Item>) {
    val worn = torchCopy(uniqueId = WORN_ID, durability = wornDurability).apply { equipped = true }
    add(id, Inventory((bag + worn).toMutableList()))
    add(id, Equipment(availableSlotMask = EquipmentSlots.ALL).apply {
      equip(
        EquipmentSlot.LIGHT_SOURCE,
        Equipment.EquippedItem(itemId = TORCH.id, uniqueId = WORN_ID, durability = wornDurability, maxDurability = TORCH.maxDurability)
      )
    })
  }

  private fun torchCopy(uniqueId: Long, durability: Int) = Inventory.Item(
    itemId = TORCH.id, amount = 1, weight = 30, uniqueId = uniqueId, durability = durability, maxDurability = TORCH.maxDurability
  )

  private fun light(entity: EntityId) = world.get(entity, Equipment::class)!!.get(EquipmentSlot.LIGHT_SOURCE)

  private fun inventory(entity: EntityId) = world.get(entity, Inventory::class)!!

  private companion object {
    const val ACCOUNT_ID = 1L
    const val MASTER_ID = 2L
    const val BESTIA_ID = 3L
    const val WORN_ID = 10L
    val MIDNIGHT = BestiaDateTime(year = 1, month = 1, day = 1, hour = 0, minute = 0, second = 0)
    val NOON = BestiaDateTime(year = 1, month = 1, day = 1, hour = 12, minute = 0, second = 0)
    val TORCH = Item(
      id = 35L, identifier = "torch", weight = 30, type = Item.ItemType.EQUIP,
      equipSlot = EquipmentSlot.LIGHT_SOURCE, stackable = true, maxDurability = 360, material = ItemMaterial.WOOD
    )
  }
}
