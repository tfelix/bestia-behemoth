package net.bestia.zone.item.equip

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemRepository
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals
import net.bestia.zone.item.net.EquipItemCMSG
import net.bestia.zone.item.net.EquipItemHandler
import net.bestia.zone.item.net.UnequipItemCMSG
import net.bestia.zone.item.net.UnequipItemHandler

/** A body waiting to respawn keeps what it wears: changing gear is something only the living do. */
class DeadEquipmentChangeTest {

  private val world = testWorld()
  private val sessions = ConnectionInfoService()
  private val equipmentService = mockk<EquipmentService>(relaxed = true)

  private val itemRepository = mockk<ItemRepository> {
    every { findById(SWORD.id) } returns Optional.of(SWORD)
  }

  @Test
  fun `a dead master equips nothing`() {
    deadMaster()

    equipHandler().handle(EquipItemCMSG(ACCOUNT, SWORD.id, uniqueId = 0L, slot = EquipmentSlot.RIGHT_HAND))

    verify(exactly = 0) { equipmentService.checkEquip(any(), any(), any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a dead master keeps worn gear on`() {
    val master = deadMaster()
    world.get(master, Equipment::class)!!
      .equip(EquipmentSlot.RIGHT_HAND, Equipment.EquippedItem(itemId = SWORD.id, uniqueId = 3L))

    unequipHandler().handle(world, UnequipItemCMSG(ACCOUNT, EquipmentSlot.RIGHT_HAND))

    assertEquals(SWORD.id, world.get(master, Equipment::class)!!.get(EquipmentSlot.RIGHT_HAND)?.itemId)
  }

  private fun deadMaster(): Long {
    val master = world.createEntity { id ->
      add(id, Inventory(mutableListOf(Inventory.Item(SWORD.id, amount = 1, uniqueId = 3L, stackable = false))))
      add(id, Equipment(availableSlotMask = EquipmentSlots.ALL))
      add(id, Dead())
    }
    sessions.activateSession(ACCOUNT, MASTER_ID, master)

    return master
  }

  private fun equipHandler(): EquipItemHandler {
    return EquipItemHandler(
      itemRepository = itemRepository,
      equipmentService = equipmentService,
      noviceGate = mockk(relaxed = true),
      connectionInfoService = sessions,
      inventoryService = mockk(relaxed = true),
      asyncJobExecutor = mockk(relaxed = true),
      outMessageProcessor = mockk(relaxed = true),
      deadActionGuard = DeadActionGuard(),
      world = world
    )
  }

  private fun unequipHandler(): UnequipItemHandler {
    return UnequipItemHandler(
      connectionInfoService = sessions,
      inventoryService = mockk(relaxed = true),
      asyncJobExecutor = mockk(relaxed = true),
      deadActionGuard = DeadActionGuard(),
    )
  }

  private companion object {
    const val ACCOUNT = 1L
    const val MASTER_ID = 2L
    val SWORD = Item(
      id = 11L,
      identifier = "sword",
      weight = 10,
      type = Item.ItemType.EQUIP,
      stackable = false,
      equipSlot = EquipmentSlot.RIGHT_HAND
    )
  }
}
