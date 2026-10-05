package net.bestia.zone.item

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.battle.damage.DeadActionGuard
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.item.Equipment
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ItemContainer
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertTrue

/**
 * A drop removes the item durably first and then mirrors it on the live inventory. Both halves have to take the
 * same thing, or the item ends up on the ground and in the bag at once.
 */
class DropItemHandlerTest {

  private val world: World = testWorld()
  private val inventoryService = mockk<InventoryService>()

  private val itemRepository = mockk<ItemRepository> {
    every { findById(APPLE.id) } returns Optional.of(APPLE)
    every { findById(SWORD.id) } returns Optional.of(SWORD)
  }

  @Test
  fun `dropping more than the live stack holds never reaches the database`() {
    val dropper = dropper(Inventory.Item(APPLE.id, amount = 3))

    handler(dropper).handle(DropItemCMSG(ACCOUNT_ID, APPLE.id, amount = 5))

    verify(exactly = 0) { inventoryService.removeOneFromMaster(any(), any(), any(), any()) }
  }

  /** A looted instance is minted in the database after the live copy was added, which still reads uniqueId 0. */
  @Test
  fun `a freshly looted item leaves the bag when it is dropped`() {
    every { inventoryService.removeOneFromMaster(MASTER_ID, SWORD.id, 1, 0L) } returns
      ItemContainer.RemovedItem(uniqueId = 77L, instance = null)
    val dropper = dropper(Inventory.Item(SWORD.id, amount = 1, uniqueId = 0L, stackable = false))

    handler(dropper).handle(DropItemCMSG(ACCOUNT_ID, SWORD.id, amount = 1))

    assertTrue(world.get(dropper, Inventory::class)!!.getItems().none { it.itemId == SWORD.id })
  }

  /** Worn a moment ago, so the database still has it free: only the live Equipment knows it is worn. */
  @Test
  fun `worn gear is never dropped`() {
    val dropper = dropper(Inventory.Item(SWORD.id, amount = 1, uniqueId = 0L, stackable = false))
    world.get(dropper, Equipment::class)!!
      .equip(EquipmentSlot.RIGHT_HAND, Equipment.EquippedItem(itemId = SWORD.id, uniqueId = 0L))

    handler(dropper).handle(DropItemCMSG(ACCOUNT_ID, SWORD.id, amount = 1))

    verify(exactly = 0) { inventoryService.removeOneFromMaster(any(), any(), any(), any()) }
  }

  @Test
  fun `a dead master drops nothing`() {
    val dropper = dropper(Inventory.Item(APPLE.id, amount = 3))
    world.add(dropper, Dead())

    handler(dropper).handle(DropItemCMSG(ACCOUNT_ID, APPLE.id, amount = 1))

    verify(exactly = 0) { inventoryService.removeOneFromMaster(any(), any(), any(), any()) }
  }

  private fun dropper(vararg held: Inventory.Item): EntityId {
    return world.createEntity { id ->
      add(id, Position(0, 0, 0))
      add(id, Inventory(held.toMutableList()))
      add(id, Equipment(availableSlotMask = EquipmentSlots.ALL))
    }
  }

  private fun handler(dropper: EntityId): DropItemHandler {
    val connectionInfoService = ConnectionInfoService()
    connectionInfoService.activateSession(ACCOUNT_ID, masterId = MASTER_ID, masterEntityId = dropper)

    return DropItemHandler(
      itemRepository, inventoryService, mockk(relaxed = true), connectionInfoService, DeadActionGuard(world), world
    )
  }

  private companion object {
    const val ACCOUNT_ID = 1L
    const val MASTER_ID = 2L
    val APPLE = Item(id = 10L, identifier = "apple", weight = 1, type = Item.ItemType.ETC)
    val SWORD = Item(id = 11L, identifier = "sword", weight = 10, type = Item.ItemType.ETC, stackable = false)
  }
}
