package net.bestia.zone.trade

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.skill.BasicSkillGate
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.entity.ecs.DeadActionGuard
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals

/**
 * An offer reserves its item outside the session lock, so a cancel can end the trade in between. The
 * reservation then belongs to a trade that no longer exists, and nothing else would ever give it back.
 */
class TradeServiceTest {

  private val world = testWorld()
  private val sessions = ConnectionInfoService()
  private val inventoryService = mockk<InventoryService>(relaxed = true)

  private val masterRepository = mockk<MasterRepository> {
    every { findById(any()) } answers { Optional.of(mockk<Master>(relaxed = true)) }
  }

  private val service = TradeService(
    world = world,
    connectionInfoService = sessions,
    masterRepository = masterRepository,
    inventoryService = inventoryService,
    outMessageProcessor = mockk(relaxed = true),
    basicSkillGate = mockk<BasicSkillGate> { every { mayTrade(any()) } returns true },
    asyncJobExecutor = mockk(relaxed = true),
    deadActionGuard = DeadActionGuard(),
  )

  @Test
  fun `an offer that loses the race with a cancel is given back`() {
    val offerer = player(OFFERER, OFFERER_MASTER, x = 0)
    val partner = player(PARTNER, PARTNER_MASTER, x = 1)
    service.requestTrade(OFFERER, partner)
    service.answerRequest(PARTNER, TRADE, accept = true)
    every { inventoryService.reserveForTrade(OFFERER_MASTER, TRADE, APPLE, 0L, 1) } answers {
      service.cancel(PARTNER, TRADE)
      RESERVED_APPLE
    }
    every { inventoryService.releaseTradeReservation(OFFERER_MASTER, TRADE, RESERVED_APPLE.offerSlotId) } returns
      RESERVED_APPLE

    service.offerItem(OFFERER, TRADE, APPLE, uniqueId = 0L, amount = 1)

    verify(exactly = 1) { inventoryService.releaseTradeReservation(OFFERER_MASTER, TRADE, RESERVED_APPLE.offerSlotId) }
    assertEquals(5, world.get(offerer, Inventory::class)!!.getItems().sumOf { it.amount })
  }

  @Test
  fun `a dead master offers nothing`() {
    val offerer = player(OFFERER, OFFERER_MASTER, x = 0)
    val partner = player(PARTNER, PARTNER_MASTER, x = 1)
    service.requestTrade(OFFERER, partner)
    service.answerRequest(PARTNER, TRADE, accept = true)
    world.add(offerer, Dead())

    service.offerItem(OFFERER, TRADE, APPLE, uniqueId = 0L, amount = 1)

    verify(exactly = 0) { inventoryService.reserveForTrade(any(), any(), any(), any(), any()) }
  }

  @Test
  fun `a dead master cannot ask anyone to trade`() {
    val asker = player(OFFERER, OFFERER_MASTER, x = 0)
    val partner = player(PARTNER, PARTNER_MASTER, x = 1)
    world.add(asker, Dead())

    service.requestTrade(OFFERER, partner)
    service.answerRequest(PARTNER, TRADE, accept = true)
    service.offerItem(OFFERER, TRADE, APPLE, uniqueId = 0L, amount = 1)

    verify(exactly = 0) { inventoryService.reserveForTrade(any(), any(), any(), any(), any()) }
  }

  private fun player(accountId: Long, masterId: Long, x: Long): EntityId {
    val entity = world.createEntity { id ->
      add(id, Position(x, 0, 0))
      add(id, Account(accountId))
      add(id, Inventory(mutableListOf(Inventory.Item(APPLE, amount = 5, weight = 1))))
    }
    sessions.activateSession(accountId, masterId, entity)

    return entity
  }

  private companion object {
    const val OFFERER = 1L
    const val PARTNER = 2L
    const val OFFERER_MASTER = 10L
    const val PARTNER_MASTER = 20L
    const val TRADE = 1L
    const val APPLE = 100L
    val RESERVED_APPLE = ReservedItem(
      offerSlotId = 7L,
      itemId = APPLE,
      amount = 1,
      weight = 1,
      uniqueId = 0L,
      stackable = true,
      durability = 0,
      maxDurability = 0,
      slots = 0,
      upgradeLevel = 0
    )
  }
}
