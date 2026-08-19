package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.findByIdOrThrow
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.item.ObtainItemIntent
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.item.ItemRepository
import net.bestia.zone.item.findByIdentifierOrThrow
import net.bestia.zone.mocks.GameClientMock
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.trade.AnswerTradeRequestCMSG
import net.bestia.zone.trade.CancelTradeCMSG
import net.bestia.zone.trade.ConfirmTradeCMSG
import net.bestia.zone.trade.OfferTradeItemCMSG
import net.bestia.zone.trade.RequestTradeCMSG
import net.bestia.zone.trade.RetractTradeItemCMSG
import net.bestia.zone.trade.SetTradeLockCMSG
import net.bestia.zone.trade.TradeRequestSMSG
import net.bestia.zone.trade.TradeStateSMSG
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Trading, end to end over the real message path.
 *
 * The tests that matter here are the last few: that an offered item is genuinely out of reach of everything
 * else while it is offered, that walking away gives it back, and that a completed exchange moves both sides
 * at once. Everything before them is the setup that makes those observable.
 *
 * Note the scenario suite shares one Spring context and a live tick loop, and is known to be flaky on a clean
 * tree - baseline it before reading a failure here as this feature's fault.
 */
class TradeScenarios : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var itemRepository: ItemRepository

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val player1 get() = clientPlayer1.connectedPlayerId
  private val player2 get() = clientPlayer2.connectedPlayerId

  private fun entityOf(accountId: Long): EntityId =
    connectionInfoService.getSelectedMasterEntityId(accountId)!!

  private fun appleId(): Long = itemRepository.findByIdentifierOrThrow("apple").id

  /**
   * Goes through the ECS grant path, then waits for *both* halves.
   *
   * Waiting for the live inventory alone is not enough: `ObtainItemIntentSystem` hands the database write to
   * a worker, and an offer made in between would find the item in the live copy and not in the container it
   * has to be reserved on.
   */
  private fun grant(accountId: Long, itemId: Long, amount: Int) {
    val entityId = entityOf(accountId)
    val durableBefore = durableAmount(accountId, itemId)

    world.modify(entityId) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId, amount)) }

    await {
      assertTrue(liveAmount(accountId, itemId) >= amount)
      assertTrue(durableAmount(accountId, itemId) >= durableBefore + amount)
    }
  }

  /** What the owner's container actually holds and could hand over, ignoring anything worn or promised. */
  private fun durableAmount(accountId: Long, itemId: Long): Int =
    TransactionTemplate(transactionManager).execute {
      masterRepository.findByIdOrThrow(connectionInfoService.getMasterId(accountId)).container.slots
        .filter { it.isFree && it.template.id == itemId }
        .sumOf { it.amount }
    }!!

  /**
   * Cancels and waits until it is really over. Cancelling is asynchronous, and both accounts stay claimed
   * until the cleanup lands - so a test that merely fires the message leaves the next one unable to trade.
   */
  private fun endTrade(client: GameClientMock, tradeId: Long) {
    client.sendMessage(CancelTradeCMSG(client.connectedPlayerId, tradeId))

    await {
      val state = client.tryGetLastReceived(TradeStateSMSG::class)
      assertNotNull(state)
      assertEquals(TradeStateSMSG.Status.CANCELLED, state.status)
    }
  }

  private fun liveAmount(accountId: Long, itemId: Long): Int = world.read {
    get(entityOf(accountId), Inventory::class)
      ?.getItems()
      ?.filter { it.itemId == itemId }
      ?.sumOf { it.amount }
      ?: 0
  }

  private fun openTrade(): Long {
    clientPlayer1.sendMessage(RequestTradeCMSG(player1, entityOf(player2)))

    val request = awaitValue { assertNotNull(clientPlayer2.tryGetLastReceived(TradeRequestSMSG::class)) }
    clientPlayer2.sendMessage(AnswerTradeRequestCMSG(player2, request.tradeId, accept = true))
    await { assertNotNull(clientPlayer1.tryGetLastReceived(TradeStateSMSG::class)) }

    return request.tradeId
  }

  /**
   * [await] with a value. Named apart from the base class's rather than overloading it: the two differ only
   * in return type, which is exactly the shape Kotlin cannot resolve at a call site.
   */
  private fun <T : Any> awaitValue(fn: () -> T): T {
    var result: T? = null
    await { result = fn() }
    return result!!
  }

  @Test
  @Order(1)
  fun `asking somebody nearby to trade raises a prompt on their side carrying our name`() {
    clientPlayer1.sendMessage(RequestTradeCMSG(player1, entityOf(player2)))

    val request = awaitValue { assertNotNull(clientPlayer2.tryGetLastReceived(TradeRequestSMSG::class)) }

    val ourName = masterRepository.findByIdOrThrow(connectionInfoService.getMasterId(player1)).name
    assertEquals(ourName, request.fromMasterName)
    assertEquals(entityOf(player1), request.fromEntityId)

    endTrade(clientPlayer2, request.tradeId)
  }

  @Test
  @Order(2)
  fun `declining tells the asker who declined, by name`() {
    clientPlayer1.sendMessage(RequestTradeCMSG(player1, entityOf(player2)))
    val request = awaitValue { assertNotNull(clientPlayer2.tryGetLastReceived(TradeRequestSMSG::class)) }

    clientPlayer2.sendMessage(AnswerTradeRequestCMSG(player2, request.tradeId, accept = false))

    val error = awaitValue { assertNotNull(clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.TRADE_DECLINED, error.code)

    val theirName = masterRepository.findByIdOrThrow(connectionInfoService.getMasterId(player2)).name
    assertEquals(listOf(theirName), error.args, "the client needs the name to write the chat line")
  }

  @Test
  @Order(3)
  fun `asking somebody who is already trading is refused`() {
    val tradeId = openTrade()

    clientPlayer3.sendMessage(RequestTradeCMSG(clientPlayer3.connectedPlayerId, entityOf(player2)))

    val error = awaitValue { assertNotNull(clientPlayer3.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.TRADE_TARGET_UNAVAILABLE, error.code)

    endTrade(clientPlayer1, tradeId)
  }

  @Test
  @Order(4)
  fun `an offered item leaves the inventory and comes back when it is retracted`() {
    val apple = appleId()
    grant(player1, apple, 5)
    val before = liveAmount(player1, apple)

    val tradeId = openTrade()

    clientPlayer1.sendMessage(OfferTradeItemCMSG(player1, tradeId, apple, uniqueId = 0L, amount = 2))

    val offered = awaitValue {
      val state = assertNotNull(clientPlayer1.tryGetLastReceived(TradeStateSMSG::class))
      assertEquals(1, state.ownOffer.size)
      state
    }
    assertEquals(before - 2, liveAmount(player1, apple), "an offered apple is not a held apple")

    // The partner sees the same offer from the other side, live.
    await {
      val theirView = assertNotNull(clientPlayer2.tryGetLastReceived(TradeStateSMSG::class))
      assertEquals(1, theirView.partnerOffer.size)
      assertEquals(2, theirView.partnerOffer.single().amount)
    }

    clientPlayer1.sendMessage(
      RetractTradeItemCMSG(player1, tradeId, offered.ownOffer.single().offerSlotId)
    )

    await { assertEquals(before, liveAmount(player1, apple)) }

    endTrade(clientPlayer1, tradeId)
  }

  @Test
  @Order(5)
  fun `cancelling gives every offer back and names who called it off`() {
    val apple = appleId()
    grant(player1, apple, 3)
    val before = liveAmount(player1, apple)

    val tradeId = openTrade()
    clientPlayer1.sendMessage(OfferTradeItemCMSG(player1, tradeId, apple, 0L, 3))
    await { assertEquals(before - 3, liveAmount(player1, apple)) }

    clientPlayer2.sendMessage(CancelTradeCMSG(player2, tradeId))

    await { assertEquals(before, liveAmount(player1, apple), "a cancelled trade returns everything") }

    val error = awaitValue { assertNotNull(clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.TRADE_CANCELLED, error.code)

    val theirName = masterRepository.findByIdOrThrow(connectionInfoService.getMasterId(player2)).name
    assertEquals(listOf(theirName), error.args)

    assertNull(
      clientPlayer2.tryGetLastReceived(OperationErrorSMSG::class),
      "whoever pressed Cancel is not told that somebody cancelled"
    )

    await {
      val state = clientPlayer2.tryGetLastReceived(TradeStateSMSG::class)
      assertNotNull(state)
      assertEquals(TradeStateSMSG.Status.CANCELLED, state.status)
    }
  }

  @Test
  @Order(6)
  fun `a locked side cannot add anything, and the other side changing its offer clears the lock`() {
    val apple = appleId()
    grant(player1, apple, 4)
    grant(player2, apple, 4)

    val tradeId = openTrade()

    clientPlayer1.sendMessage(SetTradeLockCMSG(player1, tradeId, locked = true))
    await {
      val state = assertNotNull(clientPlayer2.tryGetLastReceived(TradeStateSMSG::class))
      assertTrue(state.partnerLocked)
    }

    // Locking means locking: you cannot lock to reassure a partner and then quietly add to your own side.
    val heldBefore = liveAmount(player1, apple)
    clientPlayer1.sendMessage(OfferTradeItemCMSG(player1, tradeId, apple, 0L, 1))
    await {
      val state = assertNotNull(clientPlayer1.tryGetLastReceived(TradeStateSMSG::class))
      assertTrue(state.ownOffer.isEmpty(), "a locked side may not add to its own offer")
    }
    assertEquals(heldBefore, liveAmount(player1, apple), "and the refusal costs it nothing")

    // But the partner may still change theirs, and doing so has to drop the lock - otherwise locking early
    // would be a way to hold somebody to contents they have not seen.
    clientPlayer1.clearMessages()
    clientPlayer2.sendMessage(OfferTradeItemCMSG(player2, tradeId, apple, 0L, 1))

    await {
      val state = assertNotNull(clientPlayer1.tryGetLastReceived(TradeStateSMSG::class))
      assertEquals(1, state.partnerOffer.size)
      assertFalse(state.ownLocked, "their change dropped our lock")
      assertEquals(TradeStateSMSG.Status.OPEN, state.status)
    }

    endTrade(clientPlayer1, tradeId)
  }

  @Test
  @Order(7)
  fun `a confirmed trade swaps both sides and only after both have locked and both confirmed`() {
    val apple = appleId()
    grant(player1, apple, 5)
    grant(player2, apple, 5)

    val oneBefore = liveAmount(player1, apple)
    val twoBefore = liveAmount(player2, apple)

    val tradeId = openTrade()

    clientPlayer1.sendMessage(OfferTradeItemCMSG(player1, tradeId, apple, 0L, 2))
    await { assertEquals(oneBefore - 2, liveAmount(player1, apple)) }
    clientPlayer2.sendMessage(OfferTradeItemCMSG(player2, tradeId, apple, 0L, 3))
    await { assertEquals(twoBefore - 3, liveAmount(player2, apple)) }

    // Confirming before both are locked does nothing at all.
    clientPlayer1.sendMessage(ConfirmTradeCMSG(player1, tradeId))
    await {
      val state = assertNotNull(clientPlayer1.tryGetLastReceived(TradeStateSMSG::class))
      assertFalse(state.ownConfirmed, "a confirmation is only accepted once both sides are locked")
    }

    clientPlayer1.sendMessage(SetTradeLockCMSG(player1, tradeId, locked = true))
    clientPlayer2.sendMessage(SetTradeLockCMSG(player2, tradeId, locked = true))
    await {
      val state = assertNotNull(clientPlayer1.tryGetLastReceived(TradeStateSMSG::class))
      assertEquals(TradeStateSMSG.Status.LOCKED, state.status)
    }

    clientPlayer1.sendMessage(ConfirmTradeCMSG(player1, tradeId))
    await {
      val state = assertNotNull(clientPlayer2.tryGetLastReceived(TradeStateSMSG::class))
      assertTrue(state.partnerConfirmed)
    }
    assertEquals(oneBefore - 2, liveAmount(player1, apple), "one confirmation moves nothing")

    clientPlayer2.sendMessage(ConfirmTradeCMSG(player2, tradeId))

    await {
      assertEquals(oneBefore - 2 + 3, liveAmount(player1, apple))
      assertEquals(twoBefore - 3 + 2, liveAmount(player2, apple))
    }

    await {
      val state = assertNotNull(clientPlayer1.tryGetLastReceived(TradeStateSMSG::class))
      assertEquals(TradeStateSMSG.Status.COMPLETED, state.status)
    }
  }

  @Test
  @Order(8)
  fun `walking more than ten tiles apart cancels the trade and gives the offers back`() {
    val apple = appleId()
    grant(player1, apple, 4)
    val before = liveAmount(player1, apple)

    val tradeId = openTrade()
    clientPlayer1.sendMessage(OfferTradeItemCMSG(player1, tradeId, apple, 0L, 4))
    await { assertEquals(before - 4, liveAmount(player1, apple)) }

    val hereBefore = world.read { get(entityOf(player2), Position::class)!!.toVec3L() }
    world.modify(entityOf(player2)) { id -> get(id, Position::class)!!.x = hereBefore.x + 50 }

    await { assertEquals(before, liveAmount(player1, apple), "walking off gives the offer back") }

    val error = awaitValue { assertNotNull(clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.TRADE_WALKED_AWAY, error.code)

    // Put them back so a later test in this shared context still finds them next to each other.
    world.modify(entityOf(player2)) { id -> get(id, Position::class)!!.x = hereBefore.x }
    assertNull(clientPlayer1.tryGetLastReceived(TradeRequestSMSG::class))
  }
}
