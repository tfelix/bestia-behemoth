package net.bestia.zone.item.net

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.Element
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.battle.ammo.AmmoType
import net.bestia.zone.item.equip.EquipmentSlot
import net.bestia.zone.item.equip.EquipmentSlots
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SetQuiverHandlerTest {

  private val world = testWorld()
  private val sessions = ConnectionInfoService()
  private val inventoryService = mockk<InventoryService>(relaxed = true)
  private val outMessages = mockk<OutMessageProcessor>(relaxed = true)
  private val asyncJobs = mockk<AsyncJobExecutor> {
    every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }
  private val itemRepository = mockk<ItemRepository> {
    every { findAll() } returns listOf(ARROW, FIRE_ARROW, SWORD)
  }

  private val handler = SetQuiverHandler(
    connectionInfoService = sessions,
    templates = ItemTemplateRegistry(itemRepository),
    inventoryService = inventoryService,
    asyncJobExecutor = asyncJobs,
    outMessageProcessor = outMessages,
    deadActionGuard = DeadActionGuard(),
  )

  private var master = 0L

  @BeforeEach
  fun setUp() {
    master = world.createEntity { id -> add(id, Equipment(availableSlotMask = EquipmentSlots.ALL)) }
    sessions.activateSession(ACCOUNT, MASTER_ID, master)
  }

  @Test
  fun `points the quiver at ammo in the given order and persists it`() {
    handler.handle(world, SetQuiverCMSG(ACCOUNT, listOf(FIRE_ARROW.id, ARROW.id)))

    assertEquals(listOf(FIRE_ARROW.id, ARROW.id), quiver())
    verify { inventoryService.setQuiver(MASTER_ID, null, listOf(FIRE_ARROW.id, ARROW.id)) }
  }

  @Test
  fun `refuses anything that is not ammo`() {
    handler.handle(world, SetQuiverCMSG(ACCOUNT, listOf(ARROW.id)))

    handler.handle(world, SetQuiverCMSG(ACCOUNT, listOf(ARROW.id, SWORD.id)))

    assertEquals(listOf(ARROW.id), quiver())
    verify { outMessages.sendToPlayer(ACCOUNT, OperationErrorSMSG(OpError.EQUIP_NOT_ALLOWED)) }
    verify(exactly = 1) { inventoryService.setQuiver(any(), any(), any()) }
  }

  @Test
  fun `refuses more entries than the quiver holds, and repeats`() {
    handler.handle(world, SetQuiverCMSG(ACCOUNT, List(Equipment.QUIVER_SIZE + 1) { ARROW.id + it * 1000 }))
    handler.handle(world, SetQuiverCMSG(ACCOUNT, listOf(ARROW.id, ARROW.id)))

    assertEquals(emptyList(), quiver())
    verify(exactly = 0) { inventoryService.setQuiver(any(), any(), any()) }
  }

  @Test
  fun `an empty list clears the quiver`() {
    handler.handle(world, SetQuiverCMSG(ACCOUNT, listOf(ARROW.id)))

    handler.handle(world, SetQuiverCMSG(ACCOUNT, emptyList()))

    assertEquals(emptyList(), quiver())
  }

  private fun quiver(): List<Long> {
    return world.get(master, Equipment::class)!!.getQuiver()
  }

  private companion object {
    const val ACCOUNT = 1L
    const val MASTER_ID = 2L

    val ARROW = ammo(96L, "arrow", Element.NORMAL)
    val FIRE_ARROW = ammo(97L, "fire_arrow", Element.FIRE)
    val SWORD = Item(
      id = 11L,
      identifier = "sword",
      weight = 10,
      type = Item.ItemType.EQUIP,
      equipSlot = EquipmentSlot.RIGHT_HAND,
      material = ItemMaterial.METAL
    )

    fun ammo(id: Long, identifier: String, element: Element): Item {
      return Item(
        id = id,
        identifier = identifier,
        weight = 1,
        type = Item.ItemType.AMMO,
        ammoType = AmmoType.ARROW,
        element = element,
        material = ItemMaterial.WOOD
      )
    }
  }
}
