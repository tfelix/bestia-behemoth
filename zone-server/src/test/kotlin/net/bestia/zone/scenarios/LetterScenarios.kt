package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.item.ecs.takeFromLive
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.postal.LetterService
import net.bestia.zone.postal.net.DeleteLetterCMSG
import net.bestia.zone.postal.net.LetterArrivedSMSG
import net.bestia.zone.postal.net.LetterSentSMSG
import net.bestia.zone.postal.net.LettersSMSG
import net.bestia.zone.postal.net.ReadLetterCMSG
import net.bestia.zone.postal.net.RequestLettersCMSG
import net.bestia.zone.postal.net.SendLetterCMSG
import net.bestia.zone.postal.persistence.LetterRepository
import net.bestia.zone.session.ConnectionInfoService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Letters by pigeon, end to end over the real message path. */
class LetterScenarios : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var letters: LetterRepository

  @Autowired
  private lateinit var commodityItems: CommodityItems

  @Autowired
  private lateinit var reserve: WorldReserve

  @Autowired
  private lateinit var inventoryService: InventoryService

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val sender get() = clientPlayer1.connectedPlayerId

  private val recipient get() = clientPlayer2.connectedPlayerId

  @AfterEach
  fun clearLetters() {
    TransactionTemplate(transactionManager).execute { letters.deleteAll() }
  }

  @Test
  fun `a letter costs its postage and reaches the recipient at once`() {
    val coin = commodityItems.coinItemId()!!
    grantCoins(5)
    val coinsBefore = durableCoins()
    val reserveBefore = world.read { reserve.available() }

    clientPlayer1.sendMessage(SendLetterCMSG(sender, "player2", "The bridge", "It is down."))

    val sent = awaitValue { assertNotNull(clientPlayer1.tryGetLastReceived(LetterSentSMSG::class)) }
    assertEquals("player2", sent.recipientName)
    val arrived = awaitValue { assertNotNull(clientPlayer2.tryGetLastReceived(LetterArrivedSMSG::class)) }
    assertEquals("player1", arrived.letter.senderName)
    assertEquals("The bridge", arrived.letter.subject)
    assertEquals("It is down.", arrived.letter.body)

    assertEquals(coinsBefore - LetterService.POSTAGE, durableCoins())
    await { assertEquals(coinsBefore - LetterService.POSTAGE, liveAmount(coin)) }
    assertEquals(reserveBefore + LetterService.POSTAGE, world.read { reserve.available() }, 1e-9)
  }

  @Test
  fun `a letter to a name nobody has costs nothing and says so`() {
    grantCoins(1)
    val coinsBefore = durableCoins()

    clientPlayer1.sendMessage(SendLetterCMSG(sender, "nobodyByThisName", "Hello", "Anyone?"))

    val error = awaitValue { assertNotNull(clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.MAIL_RECIPIENT_UNKNOWN, error.code)
    assertEquals(listOf("nobodyByThisName"), error.args)
    assertEquals(coinsBefore, durableCoins())
  }

  @Test
  fun `a sender without gold sends nothing`() {
    val coin = commodityItems.coinItemId()!!
    val purse = durableCoins()
    inventoryService.consumeAll(connectionInfoService.getMasterId(sender), listOf(coin to purse))
    world.takeFromLive(connectionInfoService.getSelectedMasterEntityId(sender), ReservedItem.stack(coin, purse, weight = 0))

    clientPlayer1.sendMessage(SendLetterCMSG(sender, "player2", "Hello", "Anyone?"))

    val error = awaitValue { assertNotNull(clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.MAIL_CANNOT_AFFORD, error.code)
    assertEquals(0L, letters.count())
  }

  @Test
  fun `a letter waits in the mailbox until it is read and thrown away`() {
    grantCoins(1)
    clientPlayer1.sendMessage(SendLetterCMSG(sender, "player2", "Hello", "Anyone?"))
    val letterId = awaitValue { assertNotNull(clientPlayer2.tryGetLastReceived(LetterArrivedSMSG::class)) }.letter.letterId

    clientPlayer2.sendMessage(ReadLetterCMSG(recipient, letterId))
    clientPlayer2.sendMessage(RequestLettersCMSG(recipient))
    val mailbox = awaitValue { assertNotNull(clientPlayer2.tryGetLastReceived(LettersSMSG::class)) }
    assertTrue(mailbox.letters.single { it.letterId == letterId }.read)

    clientPlayer2.clearMessages()
    clientPlayer2.sendMessage(DeleteLetterCMSG(recipient, letterId))
    await {
      val emptied = assertNotNull(clientPlayer2.tryGetLastReceived(LettersSMSG::class))
      assertTrue(emptied.letters.none { it.letterId == letterId })
    }
  }

  private fun grantCoins(amount: Int) {
    val before = durableCoins()
    val coin = commodityItems.coinItemId()!!
    world.modify(connectionInfoService.getSelectedMasterEntityId(sender)) { id ->
      add(id, ObtainItemIntent.CreateItemIntent(coin, amount))
    }

    await { assertTrue(durableCoins() >= before + amount) }
  }

  private fun liveAmount(itemId: Long): Int {
    return world.read {
      get(connectionInfoService.getSelectedMasterEntityId(sender), Inventory::class)
        ?.getItems()?.filter { it.itemId == itemId }?.sumOf { it.amount } ?: 0
    }
  }

  private fun durableCoins(): Int {
    val coin = commodityItems.coinItemId()!!
    return TransactionTemplate(transactionManager).execute {
      masterRepository.findByIdOrThrow(connectionInfoService.getMasterId(sender)).container.slots
        .filter { it.isFree && it.template.id == coin }
        .sumOf { it.amount }
    }!!
  }

  private fun <T : Any> awaitValue(fn: () -> T): T {
    var result: T? = null
    await { result = fn() }
    return result!!
  }
}
