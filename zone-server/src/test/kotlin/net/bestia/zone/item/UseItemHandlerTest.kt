package net.bestia.zone.item

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.battle.damage.DeadActionGuard
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ItemContainer
import net.bestia.zone.item.script.ItemScriptExecutionService
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.util.Optional

/**
 * A used item's effect cannot be taken back, so it only happens once the database has given the item up.
 */
class UseItemHandlerTest {

  private val world = testWorld()
  private val inventoryService = mockk<InventoryService>(relaxed = true)
  private val scripts = mockk<ItemScriptExecutionService>()

  private val itemRepository = mockk<ItemRepository> {
    every { findById(POTION.id) } returns Optional.of(POTION)
  }

  private val inlineJobs = mockk<AsyncJobExecutor> {
    every { submit(any<Long>(), any()) } answers { secondArg<() -> Unit>().invoke() }
  }

  @Test
  fun `an item the database no longer holds has no effect`() {
    every { inventoryService.removeOneFromMaster(MASTER_ID, POTION.id, 1, 0L) } returns null
    val user = user()

    handler(user).handle(UseItemCMSG(ACCOUNT_ID, POTION.id, ScriptArgs.EMPTY))

    verify(exactly = 0) { scripts.useItem(any(), any(), any(), any()) }
  }

  @Test
  fun `an item whose script refuses it is given back durably`() {
    every { inventoryService.removeOneFromMaster(MASTER_ID, POTION.id, 1, 0L) } returns
      ItemContainer.RemovedItem(uniqueId = 0L, instance = null)
    every { scripts.useItem(any(), any(), POTION, any()) } returns false
    val user = user()

    handler(user).handle(UseItemCMSG(ACCOUNT_ID, POTION.id, ScriptArgs.EMPTY))

    verify { inventoryService.grantToMaster(MASTER_ID, POTION.id, 1, 0L) }
  }

  private fun user(): EntityId {
    return world.createEntity { id -> add(id, Inventory(mutableListOf(Inventory.Item(POTION.id, amount = 3)))) }
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
      world = world
    )
  }

  private companion object {
    const val ACCOUNT_ID = 1L
    const val MASTER_ID = 2L
    val POTION = Item(id = 20L, identifier = "potion", weight = 1, type = Item.ItemType.USABLE, script = "Potion")
  }
}
