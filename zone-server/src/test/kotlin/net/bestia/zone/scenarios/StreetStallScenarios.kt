package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.item.persistence.findByIdentifierOrThrow
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.postal.persistence.ParcelRepository
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.trade.net.StallBuyCMSG
import net.bestia.zone.trade.net.StallListCMSG
import net.bestia.zone.trade.net.StallUnlistCMSG
import net.bestia.zone.trade.stall.StreetStall
import net.bestia.zone.trade.stall.StreetStallSMSG
import net.bestia.zone.trade.stall.StreetStalls
import net.bestia.zone.trade.persistence.StreetStallListingRepository
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A Trader's street stall over the real message path: pitching, listing, buying and packing up. */
class StreetStallScenarios : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var stalls: StreetStalls

  @Autowired
  private lateinit var listings: StreetStallListingRepository

  @Autowired
  private lateinit var parcels: ParcelRepository

  @Autowired
  private lateinit var itemRepository: ItemRepository

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var commodityItems: CommodityItems

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val seller get() = clientPlayer1.connectedPlayerId
  private val buyer get() = clientPlayer2.connectedPlayerId

  private lateinit var sellerHome: Vec3L
  private lateinit var buyerHome: Vec3L

  @BeforeEach
  fun rememberWhereEveryoneStands() {
    sellerHome = positionOf(seller)
    buyerHome = positionOf(buyer)
  }

  @AfterEach
  fun packUp() {
    world.read {
      query(StreetStall::class).each { id -> remove(id, StreetStall::class); destroy(id) }
    }
    moveTo(seller, sellerHome)
    moveTo(buyer, buyerHome)
    TransactionTemplate(transactionManager).execute {
      listings.deleteAll()
      parcels.deleteAll()
    }
  }

  @Test
  fun `a buyer at the stall gets the goods and the seller is paid into the bag`() {
    val apple = itemRepository.findByIdentifierOrThrow("apple").id
    val coin = commodityItems.coinItemId()!!
    val stall = pitch(maxListings = 3)
    val listingId = listApples(stall, amount = 2, price = 30L)

    moveTo(buyer, sellerHome)
    grant(coin, 30, buyer)
    val buyerApples = durableAmount(apple, buyer)
    val sellerCoins = durableAmount(coin, seller)
    clientPlayer2.clearMessages()

    clientPlayer2.sendMessage(StallBuyCMSG(buyer, stall, listingId))

    await { assertEquals(buyerApples + 2, durableAmount(apple, buyer)) }
    assertEquals(sellerCoins + 30, durableAmount(coin, seller))
    await { assertEquals(sellerCoins + 30, liveAmount(coin, seller)) }
    await { assertTrue(assertNotNull(clientPlayer2.tryGetLastReceived(StreetStallSMSG::class)).rows.isEmpty()) }
  }

  @Test
  fun `a buyer too far from the stall is told so`() {
    val stall = pitch(maxListings = 3)
    val listingId = listApples(stall, amount = 1, price = 10L)
    moveTo(buyer, Vec3L(sellerHome.x - 20, sellerHome.y, sellerHome.z))
    clientPlayer2.clearMessages()

    clientPlayer2.sendMessage(StallBuyCMSG(buyer, stall, listingId))

    val error = awaitValue { assertNotNull(clientPlayer2.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.STALL_OUT_OF_RANGE, error.code)
  }

  @Test
  fun `a full stall takes no more listings`() {
    val stall = pitch(maxListings = 1)
    listApples(stall, amount = 1, price = 10L)
    val apple = itemRepository.findByIdentifierOrThrow("apple").id
    grant(apple, 1)
    clientPlayer1.clearMessages()

    clientPlayer1.sendMessage(StallListCMSG(seller, stall, heldPileOf(apple, 1), amount = 1, price = 10L))

    val error = awaitValue { assertNotNull(clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.STALL_FULL, error.code)
  }

  @Test
  fun `taking an offer back returns the goods`() {
    val apple = itemRepository.findByIdentifierOrThrow("apple").id
    val stall = pitch(maxListings = 3)
    val listingId = listApples(stall, amount = 2, price = 10L)
    val listed = durableAmount(apple, seller)
    clientPlayer1.clearMessages()

    clientPlayer1.sendMessage(StallUnlistCMSG(seller, stall, listingId))

    await { assertEquals(listed + 2, durableAmount(apple, seller)) }
  }

  @Test
  fun `walking away packs the stall up and the goods come back`() {
    val apple = itemRepository.findByIdentifierOrThrow("apple").id
    val stall = pitch(maxListings = 3)
    listApples(stall, amount = 2, price = 10L)
    val listed = durableAmount(apple, seller)
    clientPlayer1.clearMessages()

    moveTo(seller, Vec3L(sellerHome.x + 20, sellerHome.y, sellerHome.z))

    await { assertTrue(assertNotNull(clientPlayer1.tryGetLastReceived(StreetStallSMSG::class)).closed) }
    assertEquals(listed + 2, durableAmount(apple, seller))
    assertEquals(0L, listings.count())
  }

  @Test
  fun `a second stall is refused while the first stands`() {
    pitch(maxListings = 3)

    val refusal = world.modify(masterEntity(seller)) { id -> stalls.pitch(this, id, 3, 3600) }

    assertEquals(OpError.STALL_ALREADY_OPEN, refusal)
  }

  @Test
  fun `what a restart left on a stall goes home by post`() {
    val stall = pitch(maxListings = 3)
    listApples(stall, amount = 2, price = 10L)

    stalls.returnLeftovers()

    assertEquals(0L, listings.count())
    val parcelCount = TransactionTemplate(transactionManager).execute {
      parcels.findByRecipientMasterIdOrderByLatestArrival(connectionInfoService.getMasterId(seller)).size
    }
    assertEquals(1, parcelCount)
  }

  /** @return the stall's entity id, from the window its seller is sent */
  private fun pitch(maxListings: Int): EntityId {
    clientPlayer1.clearMessages()
    world.modify(masterEntity(seller)) { id -> stalls.pitch(this, id, maxListings, 3600) }

    return awaitValue { assertNotNull(clientPlayer1.tryGetLastReceived(StreetStallSMSG::class)).stallEntityId }
  }

  /** @return the new listing's id */
  private fun listApples(stall: EntityId, amount: Int, price: Long): Long {
    val apple = itemRepository.findByIdentifierOrThrow("apple").id
    grant(apple, amount)
    clientPlayer1.clearMessages()

    clientPlayer1.sendMessage(StallListCMSG(seller, stall, heldPileOf(apple, amount), amount = amount, price = price))

    return awaitValue {
      assertNotNull(clientPlayer1.tryGetLastReceived(StreetStallSMSG::class)).rows.single().listingId
    }
  }

  private fun positionOf(accountId: Long): Vec3L {
    return world.read { get(masterEntity(accountId), Position::class)!!.toVec3L() }
  }

  private fun moveTo(accountId: Long, at: Vec3L) {
    world.modify(masterEntity(accountId)) { id -> get(id, Position::class)!!.stepTo(at.x, at.y, at.z) }
  }

  private fun masterEntity(accountId: Long): EntityId = connectionInfoService.getSelectedMasterEntityId(accountId)

  private fun grant(itemId: Long, amount: Int, accountId: Long = seller) {
    val durableBefore = durableAmount(itemId, accountId)
    world.modify(masterEntity(accountId)) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId, amount)) }

    await { assertTrue(durableAmount(itemId, accountId) >= durableBefore + amount) }
  }

  private fun liveAmount(itemId: Long, accountId: Long): Int {
    return world.read {
      get(masterEntity(accountId), Inventory::class)?.getItems()?.filter { it.itemId == itemId }?.sumOf { it.amount } ?: 0
    }
  }

  private fun heldPileOf(itemId: Long, amount: Int): Long {
    return world.read {
      get(masterEntity(seller), Inventory::class)!!.getItems().first { it.itemId == itemId && it.amount >= amount }.playerItemId
    }
  }

  private fun durableAmount(itemId: Long, accountId: Long): Int {
    return TransactionTemplate(transactionManager).execute {
      masterRepository.findByIdOrThrow(connectionInfoService.getMasterId(accountId)).container.slots
        .filter { it.isFree && it.template.id == itemId }
        .sumOf { it.amount }
    }!!
  }

  private fun <T : Any> awaitValue(fn: () -> T): T {
    var result: T? = null
    await { result = fn() }
    return result!!
  }
}
