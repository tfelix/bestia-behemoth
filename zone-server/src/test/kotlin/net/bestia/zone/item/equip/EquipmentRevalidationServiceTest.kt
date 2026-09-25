package net.bestia.zone.item.equip

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.account.master.skill.MasterSkillTreeNode
import net.bestia.zone.account.master.skill.MasterSkillTreeRegistry
import net.bestia.zone.account.master.skill.NoviceGate
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.level.Level
import net.bestia.zone.ecs.battle.skill.KnownSkills
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.item.Equipment
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.item.Item
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The sweep that takes off gear its wearer no longer qualifies for - Ragnarok Online's `pc_checkitem`.
 *
 * What matters here is that the durable half and the live half move together. A removal that reached only the
 * ECS would come back at the next login, because `MasterEntitySpawner` replays the container rather than
 * re-deciding what may be worn; one that reached only the container would leave the player looking at gear
 * the server no longer believes in.
 */
class EquipmentRevalidationServiceTest {

  private val world: World = testWorld()
  private val itemRepository = mockk<ItemRepository>()
  private val inventoryService = mockk<InventoryService>(relaxed = true)
  private val outMessageProcessor = mockk<OutMessageProcessor>(relaxed = true)

  /** Runs the write-behind inline, so the durable half is observable by the time `revalidate` returns. */
  private val asyncJobExecutor = mockk<AsyncJobExecutor>().also {
    every { it.submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }

  private val registry = MasterSkillTreeRegistry().apply {
    load(
      listOf(
        MasterSkillTreeNode(skillId = BASIC_SKILL_ID, maxLevel = 5, tree = "NOVICE"),
        MasterSkillTreeNode(skillId = CARPENTRY_ID, maxLevel = 10, tree = "CRAFTSMAN")
      )
    )
  }

  private val service = EquipmentRevalidationService(
    world = world,
    itemRepository = itemRepository,
    equipmentService = EquipmentService(),
    noviceGate = NoviceGate(registry),
    inventoryService = inventoryService,
    asyncJobExecutor = asyncJobExecutor,
    outMessageProcessor = outMessageProcessor
  )

  private val noviceShirt = Item(
    id = 32L, identifier = "novice_shirt", weight = 120, type = Item.ItemType.EQUIP,
    equipSlot = EquipmentSlot.ARMOR, level = 1, noviceOnly = true
  )
  private val shoes = Item(
    id = 4L, identifier = "shoes", weight = 80, type = Item.ItemType.EQUIP,
    equipSlot = EquipmentSlot.FOOTGEAR, level = 1
  )

  init {
    every { itemRepository.findAllById(any()) } answers {
      val ids = firstArg<Iterable<Long>>().toSet()

      listOf(noviceShirt, shoes).filter { it.id in ids }
    }
  }

  /** A master wearing both pieces, knowing whatever [learned] says. */
  private fun givenWearer(vararg learned: Pair<Long, Int>): EntityId {
    return world.createEntity { id ->
      add(id, Account(ACCOUNT_ID))
      add(id, Level(10))
      add(id, KnownSkills(learned.toMap().toMutableMap()))
      add(
        id,
        Inventory(
          mutableListOf(
            inventoryItem(noviceShirt, SHIRT_UNIQUE_ID),
            inventoryItem(shoes, SHOES_UNIQUE_ID)
          )
        )
      )
      add(
        id,
        Equipment(EquipmentSlots.ALL).apply {
          equip(EquipmentSlot.ARMOR, Equipment.EquippedItem(itemId = noviceShirt.id, uniqueId = SHIRT_UNIQUE_ID))
          equip(EquipmentSlot.FOOTGEAR, Equipment.EquippedItem(itemId = shoes.id, uniqueId = SHOES_UNIQUE_ID))
        }
      )
    }
  }

  private fun inventoryItem(item: Item, uniqueId: Long): Inventory.Item {
    return Inventory.Item(
      itemId = item.id,
      amount = 1,
      weight = item.weight,
      uniqueId = uniqueId,
      stackable = false,
      equipped = true
    )
  }

  private fun equipmentOf(entityId: EntityId): Equipment {
    return world.read { get(entityId, Equipment::class) }!!
  }

  @Test
  fun `a master who is still a novice keeps everything`() {
    val entityId = givenWearer(BASIC_SKILL_ID to 5)

    service.revalidate(MASTER_ID, entityId)

    assertNotNull(equipmentOf(entityId).get(EquipmentSlot.ARMOR))
    verify(exactly = 0) { inventoryService.unequip(any(), any(), any()) }
    verify(exactly = 0) { outMessageProcessor.sendToPlayer(any<Long>(), any<SMSG>()) }
  }

  @Test
  fun `novice-only gear comes off once the master has specialised`() {
    val entityId = givenWearer(BASIC_SKILL_ID to 5, CARPENTRY_ID to 1)

    service.revalidate(MASTER_ID, entityId)

    assertNull(equipmentOf(entityId).get(EquipmentSlot.ARMOR), "the shirt should be off")
  }

  /** Ordinary gear is not collateral: the sweep judges each piece on its own rules. */
  @Test
  fun `gear without the flag is left alone`() {
    val entityId = givenWearer(CARPENTRY_ID to 1)

    service.revalidate(MASTER_ID, entityId)

    assertNotNull(equipmentOf(entityId).get(EquipmentSlot.FOOTGEAR), "the shoes should stay on")
  }

  @Test
  fun `the removal is written through to the container`() {
    val entityId = givenWearer(CARPENTRY_ID to 1)

    service.revalidate(MASTER_ID, entityId)

    verify(exactly = 1) { inventoryService.unequip(MASTER_ID, null, EquipmentSlot.ARMOR) }
    verify(exactly = 0) { inventoryService.unequip(MASTER_ID, null, EquipmentSlot.FOOTGEAR) }
  }

  @Test
  fun `the inventory marker and the recalc marker both follow`() {
    val entityId = givenWearer(CARPENTRY_ID to 1)

    service.revalidate(MASTER_ID, entityId)

    val stillEquipped = world.read {
      get(entityId, Inventory::class)!!.getItems().first { it.uniqueId == SHIRT_UNIQUE_ID }.equipped
    }

    assertFalse(stillEquipped, "the inventory view must stop calling it worn")
    assertTrue(world.has(entityId, IsStatusValueDirty::class), "losing gear changes the derived values")
  }

  @Test
  fun `the player is told why, once`() {
    val entityId = givenWearer(CARPENTRY_ID to 1)
    val sent = slot<SMSG>()

    service.revalidate(MASTER_ID, entityId)

    verify(exactly = 1) { outMessageProcessor.sendToPlayer(ACCOUNT_ID, capture(sent)) }
    assertEquals(OperationErrorProto.OpError.EQUIP_NOVICE_ONLY, (sent.captured as OperationErrorSMSG).code)
  }

  private companion object {
    const val MASTER_ID = 7L
    const val ACCOUNT_ID = 1L
    const val BASIC_SKILL_ID = 1L
    const val CARPENTRY_ID = 20L
    const val SHIRT_UNIQUE_ID = 101L
    const val SHOES_UNIQUE_ID = 102L
  }
}
