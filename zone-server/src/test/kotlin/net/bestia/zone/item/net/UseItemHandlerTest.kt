package net.bestia.zone.item.net

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.item.script.ItemScriptExecutionService
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.util.Optional
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.item.material.ItemMaterial
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.skill.NoviceGate
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.tree.MasterSkillTreeNode
import net.bestia.zone.skill.tree.MasterSkillTreeRegistry

/**
 * A used item's effect cannot be taken back, so it only happens once the database has given the item up.
 */
class UseItemHandlerTest {

  private val world = testWorld()
  private val inventoryService = mockk<InventoryService>(relaxed = true)
  private val scripts = mockk<ItemScriptExecutionService>()

  private val itemRepository = mockk<ItemRepository> {
    every { findById(POTION.id) } returns Optional.of(POTION)
    every { findById(NOVICE_POTION.id) } returns Optional.of(NOVICE_POTION)
  }

  private val messages = mockk<OutMessageProcessor>(relaxed = true)

  private val potions = Inventory.Item(POTION.id, amount = 3)
  private val novicePotions = Inventory.Item(NOVICE_POTION.id, amount = 3)

  private val skillTree = MasterSkillTreeRegistry().apply {
    load(listOf(MasterSkillTreeNode(skillId = CARPENTRY_ID, identifier = "CARPENTRY", maxLevel = 5, tree = "CRAFTSMAN")))
  }

  private val inlineJobs = mockk<AsyncJobExecutor> {
    every { submit(any<Long>(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }

  @Test
  fun `an item the database no longer holds has no effect`() {
    every { inventoryService.removeOneFromOwner(MASTER_ID, null, POTION.id, 1, 0L) } returns null
    val user = user()

    handler(user).handle(UseItemCMSG(ACCOUNT_ID, potions.playerItemId, ScriptArgs.EMPTY))

    verify(exactly = 0) { scripts.useItem(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `an item whose script refuses it is given back durably`() {
    every { inventoryService.removeOneFromOwner(MASTER_ID, null, POTION.id, 1, 0L) } returns
      ItemContainer.RemovedItem(uniqueId = 0L, instance = null)
    every { scripts.useItem(any(), any(), POTION, any(), any()) } returns false
    val user = user()

    handler(user).handle(UseItemCMSG(ACCOUNT_ID, potions.playerItemId, ScriptArgs.EMPTY))

    verify { inventoryService.grantToOwner(MASTER_ID, null, POTION.id, 1, 0L) }
  }

  @Test
  fun `a novice-only item is refused to a specialised master, before the database gives it up`() {
    val user = user(KnownSkills(mutableMapOf(CARPENTRY_ID to 1)))

    handler(user).handle(UseItemCMSG(ACCOUNT_ID, novicePotions.playerItemId, ScriptArgs.EMPTY))

    verify(exactly = 0) { inventoryService.removeOneFromOwner(any(), any(), any(), any(), any()) }
    verify(exactly = 0) { scripts.useItem(any(), any(), any(), any(), any()) }
    verify { messages.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OperationErrorProto.OpError.ITEM_NOVICE_ONLY)) }
  }

  @Test
  fun `a novice may use a novice-only item`() {
    every { inventoryService.removeOneFromOwner(MASTER_ID, null, NOVICE_POTION.id, 1, 0L) } returns
      ItemContainer.RemovedItem(uniqueId = 0L, instance = null)
    every { scripts.useItem(any(), any(), NOVICE_POTION, any(), any()) } returns true
    val user = user(KnownSkills(mutableMapOf()))

    handler(user).handle(UseItemCMSG(ACCOUNT_ID, novicePotions.playerItemId, ScriptArgs.EMPTY))

    verify { scripts.useItem(any(), any(), NOVICE_POTION, any(), any()) }
  }

  @Test
  fun `a novice-only item is refused to a player bestia`() {
    val bestia = holder()

    handler(bestia).handle(UseItemCMSG(ACCOUNT_ID, novicePotions.playerItemId, ScriptArgs.EMPTY))

    verify(exactly = 0) { inventoryService.removeOneFromOwner(any(), any(), any(), any(), any()) }
    verify { messages.sendToPlayer(ACCOUNT_ID, OperationErrorSMSG(OperationErrorProto.OpError.ITEM_NOVICE_ONLY)) }
  }

  private fun user(knownSkills: KnownSkills = KnownSkills(mutableMapOf())): EntityId {
    return holder { id ->
      add(id, Master(MASTER_ID))
      add(id, knownSkills)
    }
  }

  private fun holder(configure: World.(EntityId) -> Unit = {}): EntityId {
    return world.createEntity { id ->
      add(
        id,
        Inventory(mutableListOf(potions, novicePotions))
      )
      configure(id)
    }
  }

  private fun handler(user: EntityId): UseItemHandler {
    val connectionInfoService = ConnectionInfoService()
    connectionInfoService.activateSession(ACCOUNT_ID, masterId = MASTER_ID, masterEntityId = user)

    return UseItemHandler(
      itemScriptExecutionService = scripts,
      itemRepository = itemRepository,
      connectionInfoService = connectionInfoService,
      inventoryService = inventoryService,
      asyncJobExecutor = inlineJobs,
      deadActionGuard = DeadActionGuard(),
      noviceGate = NoviceGate(skillTree),
      outMessageProcessor = messages,
      world = world
    )
  }

  private companion object {
    const val ACCOUNT_ID = 1L
    const val MASTER_ID = 2L
    const val CARPENTRY_ID = 30L
    val POTION = Item(id = 20L, identifier = "potion", weight = 1, type = Item.ItemType.USABLE, script = "Potion", material = ItemMaterial.GLASS)
    val NOVICE_POTION = Item(
      id = 21L, identifier = "novice_potion", weight = 1, type = Item.ItemType.USABLE, script = "NovicePotion",
      material = ItemMaterial.GLASS, noviceOnly = true
    )
  }
}
