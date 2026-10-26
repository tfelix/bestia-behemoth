package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.auction.AuctionCategory
import net.bestia.zone.auction.AuctionClaimsSMSG
import net.bestia.zone.auction.AuctionPurchasedSMSG
import net.bestia.zone.auction.AuctionFees
import net.bestia.zone.auction.AuctionListingsSMSG
import net.bestia.zone.auction.AuctionQuery
import net.bestia.zone.auction.net.BuyAuctionListingCMSG
import net.bestia.zone.auction.net.CancelAuctionListingCMSG
import net.bestia.zone.auction.net.CollectAuctionClaimCMSG
import net.bestia.zone.auction.net.ListAuctionItemCMSG
import net.bestia.zone.auction.net.RequestAuctionClaimsCMSG
import net.bestia.zone.auction.net.SearchAuctionsCMSG
import net.bestia.zone.auction.persistence.AuctionClaimRepository
import net.bestia.zone.auction.persistence.AuctionListingRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.item.persistence.findByIdentifierOrThrow
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.MasterSpawnPointService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Listing and browsing over the real message path. The house is a stand-in entity beside the player, named by
 * a real start-town hall, so the reach check and the town lookup run as they do in the game.
 */
class AuctionScenarios : BestiaNoSocketScenario() {

  @Autowired
  private lateinit var connectionInfoService: ConnectionInfoService

  @Autowired
  private lateinit var world: WorldView

  @Autowired
  private lateinit var worldService: WorldService

  @Autowired
  private lateinit var siteIndex: SettlementSiteIndex

  @Autowired
  private lateinit var itemRepository: ItemRepository

  @Autowired
  private lateinit var masterRepository: MasterRepository

  @Autowired
  private lateinit var listings: AuctionListingRepository

  @Autowired
  private lateinit var claims: AuctionClaimRepository

  @Autowired
  private lateinit var commodityItems: CommodityItems

  @Autowired
  private lateinit var reserve: WorldReserve

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val player get() = clientPlayer1.connectedPlayerId

  private val buyer get() = clientPlayer2.connectedPlayerId

  private var house: EntityId = 0L

  /** Where the buyer stands, so both players are in reach of a house of the same market. */
  private var buyersHouse: EntityId = 0L

  @BeforeAll
  fun placeHouses() {
    val hallPropId = world.read {
      SettlementSpawnPoints.choose(worldService.generated, MasterSpawnPointService.HOMES_HELD)
        .take(MasterSpawnPointService.HOMES_OFFERED)
        .flatMap { siteIndex.siteOf(it.settlementIndex)?.buildingsOf(BuildingFunction.MARKET).orEmpty() }
        .first()
        .propId
    }

    house = houseBeside(player, hallPropId)
    buyersHouse = houseBeside(buyer, hallPropId)
  }

  private fun houseBeside(accountId: Long, hallPropId: Long): EntityId {
    val standingAt = world.read { get(masterEntity(accountId), Position::class)!!.toVec3L() }

    return world.read {
      createEntity { id ->
        add(id, PropPose(standingAt, yaw = 0f))
        add(id, StaticVisual(StaticEntityKind.AUCTION_HOUSE, variant = 0, heightDm = 40, halfLengthDm = 60, halfWidthDm = 40))
        add(id, WorldObjectIdentity(hallPropId, latticeVersion = 1L))
      }
    }
  }

  @AfterEach
  fun clearListings() {
    TransactionTemplate(transactionManager).execute {
      listings.deleteAll()
      claims.deleteAll()
    }
  }

  @AfterAll
  fun removeHouse() {
    world.modify(house) { id -> destroy(id) }
    world.modify(buyersHouse) { id -> destroy(id) }
  }

  @Test
  fun `listing moves the goods and the fee, and the fee goes back to the reserve`() {
    val apple = itemRepository.findByIdentifierOrThrow("apple").id
    val coin = commodityItems.coinItemId()!!
    val price = 200L
    val fee = AuctionFees.listingFee(price)

    grant(apple, 5)
    grant(coin, fee.toInt())
    val applesBefore = liveAmount(apple)
    val coinsBefore = liveAmount(coin)
    val reserveBefore = world.read { reserve.available() }

    clientPlayer1.sendMessage(ListAuctionItemCMSG(player, house, heldPileOf(apple), amount = 3, price = price))

    val mine = awaitListings { it.mine }
    val listed = mine.listings.single()
    assertEquals(apple, listed.goods.itemId)
    assertEquals(3, listed.goods.amount)
    assertEquals(price, listed.price)
    assertEquals(masterRepository.findByIdOrThrow(connectionInfoService.getMasterId(player)).name, listed.sellerName)

    await {
      assertEquals(applesBefore - 3, liveAmount(apple))
      assertEquals(coinsBefore - fee.toInt(), liveAmount(coin))
    }
    assertEquals(applesBefore - 3, durableAmount(apple), "the listed apples left the container")
    assertEquals(reserveBefore + fee, world.read { reserve.available() }, 1e-9)
  }

  @Test
  fun `a search finds the listing by category and by item`() {
    val apple = itemRepository.findByIdentifierOrThrow("apple").id
    grant(apple, 1)
    grant(commodityItems.coinItemId()!!, 10)
    clientPlayer1.sendMessage(ListAuctionItemCMSG(player, house, heldPileOf(apple), amount = 1, price = 50L))
    awaitListings { it.mine }

    assertEquals(1, search(AuctionCategory.CONSUMABLE, itemIds = emptyList()).listings.size)
    assertEquals(1, search(null, itemIds = listOf(apple)).listings.size)
    assertEquals(0, search(AuctionCategory.WEAPON, itemIds = emptyList()).listings.size)
  }

  @Test
  fun `a purse that cannot pay the fee lists nothing`() {
    val apple = itemRepository.findByIdentifierOrThrow("apple").id
    grant(apple, 1)
    val applesBefore = durableAmount(apple)
    val coins = liveAmount(commodityItems.coinItemId()!!)

    clientPlayer1.sendMessage(
      ListAuctionItemCMSG(player, house, heldPileOf(apple), amount = 1, price = (coins + 1) * 100L)
    )

    val error = awaitValue { assertNotNull(clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.AUCTION_CANNOT_AFFORD_FEE, error.code)
    assertEquals(applesBefore, durableAmount(apple))
    assertEquals(0L, listings.count())
  }

  @Test
  fun `a house out of reach answers nothing but the distance`() {
    clientPlayer1.sendMessage(
      SearchAuctionsCMSG(player, AuctionQuery(house + 1_000_000L, null, 0, 0, emptyList(), mine = false, page = 0))
    )

    val error = awaitValue { assertNotNull(clientPlayer1.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.AUCTION_OUT_OF_RANGE, error.code)
  }

  @Test
  fun `a buyer gets the goods and the seller collects the price`() {
    val apple = itemRepository.findByIdentifierOrThrow("apple").id
    val coin = commodityItems.coinItemId()!!
    val listingId = listApples(amount = 2, price = 30L)

    grant(coin, 30, buyer)
    val buyerApples = durableAmount(apple, buyer)
    val buyerCoins = durableAmount(coin, buyer)

    clientPlayer2.sendMessage(BuyAuctionListingCMSG(buyer, buyersHouse, listingId))

    val purchased = awaitValue { assertNotNull(clientPlayer2.tryGetLastReceived(AuctionPurchasedSMSG::class)) }
    assertEquals(listingId, purchased.listingId)
    assertEquals(buyerApples + 2, durableAmount(apple, buyer))
    assertEquals(buyerCoins - 30, durableAmount(coin, buyer))
    await { assertEquals(buyerCoins - 30, liveAmount(coin, buyer)) }

    clientPlayer1.sendMessage(RequestAuctionClaimsCMSG(player, house))
    val owed = awaitValue { assertNotNull(clientPlayer1.tryGetLastReceived(AuctionClaimsSMSG::class)) }
    assertEquals(30L, owed.claims.single().coins)

    val sellerCoins = durableAmount(coin)
    clientPlayer1.clearMessages()
    clientPlayer1.sendMessage(CollectAuctionClaimCMSG(player, house, claimId = 0L))

    await { assertTrue(assertNotNull(clientPlayer1.tryGetLastReceived(AuctionClaimsSMSG::class)).claims.isEmpty()) }
    assertEquals(sellerCoins + 30, durableAmount(coin))
  }

  @Test
  fun `a listing bought twice is sold once`() {
    val listingId = listApples(amount = 1, price = 10L)
    grant(commodityItems.coinItemId()!!, 20, buyer)

    clientPlayer2.sendMessage(BuyAuctionListingCMSG(buyer, buyersHouse, listingId))
    awaitValue { assertNotNull(clientPlayer2.tryGetLastReceived(AuctionPurchasedSMSG::class)) }
    clientPlayer2.sendMessage(BuyAuctionListingCMSG(buyer, buyersHouse, listingId))

    val error = awaitValue { assertNotNull(clientPlayer2.tryGetLastReceived(OperationErrorSMSG::class)) }
    assertEquals(OpError.AUCTION_LISTING_GONE, error.code)
  }

  @Test
  fun `taking a listing down gives the goods back`() {
    val apple = itemRepository.findByIdentifierOrThrow("apple").id
    val listingId = listApples(amount = 2, price = 10L)
    val applesListed = durableAmount(apple)
    clientPlayer1.clearMessages()

    clientPlayer1.sendMessage(CancelAuctionListingCMSG(player, house, listingId))

    await { assertTrue(assertNotNull(clientPlayer1.tryGetLastReceived(AuctionListingsSMSG::class)).listings.isEmpty()) }
    assertEquals(applesListed + 2, durableAmount(apple))
  }

  /** @return the new listing's id */
  private fun listApples(amount: Int, price: Long): Long {
    val apple = itemRepository.findByIdentifierOrThrow("apple").id
    grant(apple, amount)
    grant(commodityItems.coinItemId()!!, AuctionFees.listingFee(price).toInt())
    clientPlayer1.clearMessages()

    clientPlayer1.sendMessage(ListAuctionItemCMSG(player, house, heldPileOf(apple), amount = amount, price = price))

    return awaitListings { it.mine && it.listings.isNotEmpty() }.listings.single().listingId
  }

  private fun search(category: AuctionCategory?, itemIds: List<Long>): AuctionListingsSMSG {
    clientPlayer1.clearMessages()
    clientPlayer1.sendMessage(
      SearchAuctionsCMSG(player, AuctionQuery(house, category, 0, 0, itemIds, mine = false, page = 0))
    )

    return awaitListings { !it.mine }
  }

  private fun awaitListings(matching: (AuctionListingsSMSG) -> Boolean): AuctionListingsSMSG {
    return awaitValue {
      val page = assertNotNull(clientPlayer1.tryGetLastReceived(AuctionListingsSMSG::class))
      assertTrue(matching(page))
      page
    }
  }

  private fun masterEntity(accountId: Long = player): EntityId {
    return connectionInfoService.getSelectedMasterEntityId(accountId)
  }

  /** Waits for both halves, so the listing finds in the container what the live copy shows. */
  private fun grant(itemId: Long, amount: Int, accountId: Long = player) {
    val durableBefore = durableAmount(itemId, accountId)
    world.modify(masterEntity(accountId)) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId, amount)) }

    await { assertTrue(durableAmount(itemId, accountId) >= durableBefore + amount) }
  }

  private fun liveAmount(itemId: Long, accountId: Long = player): Int {
    return world.read {
      get(masterEntity(accountId), Inventory::class)?.getItems()?.filter { it.itemId == itemId }?.sumOf { it.amount } ?: 0
    }
  }

  /** The pile or copy of [itemId] the player would drag into the window: the first one held. */
  private fun heldPileOf(itemId: Long): Long {
    return world.read { get(masterEntity(), Inventory::class)!!.getItems().first { it.itemId == itemId }.playerItemId }
  }

  private fun durableAmount(itemId: Long, accountId: Long = player): Int {
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
