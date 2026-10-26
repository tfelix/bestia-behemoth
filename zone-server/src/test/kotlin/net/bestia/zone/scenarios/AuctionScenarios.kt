package net.bestia.zone.scenarios

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.auction.AuctionCategory
import net.bestia.zone.auction.AuctionFees
import net.bestia.zone.auction.AuctionListingsSMSG
import net.bestia.zone.auction.AuctionQuery
import net.bestia.zone.auction.net.ListAuctionItemCMSG
import net.bestia.zone.auction.net.SearchAuctionsCMSG
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
  private lateinit var commodityItems: CommodityItems

  @Autowired
  private lateinit var reserve: WorldReserve

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val player get() = clientPlayer1.connectedPlayerId

  private var house: EntityId = 0L

  @BeforeAll
  fun placeHouse() {
    val hallPropId = world.read {
      SettlementSpawnPoints.choose(worldService.generated, MasterSpawnPointService.HOMES_HELD)
        .take(MasterSpawnPointService.HOMES_OFFERED)
        .flatMap { siteIndex.siteOf(it.settlementIndex)?.buildingsOf(BuildingFunction.MARKET).orEmpty() }
        .first()
        .propId
    }

    val playerAt = world.read { get(masterEntity(), Position::class)!!.toVec3L() }

    house = world.read {
      createEntity { id ->
        add(id, PropPose(playerAt, yaw = 0f))
        add(id, StaticVisual(StaticEntityKind.AUCTION_HOUSE, variant = 0, heightDm = 40, halfLengthDm = 60, halfWidthDm = 40))
        add(id, WorldObjectIdentity(hallPropId, latticeVersion = 1L))
      }
    }
  }

  @AfterEach
  fun clearListings() {
    TransactionTemplate(transactionManager).execute { listings.deleteAll() }
  }

  @AfterAll
  fun removeHouse() {
    world.modify(house) { id -> destroy(id) }
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

  private fun masterEntity(): EntityId {
    return connectionInfoService.getSelectedMasterEntityId(player)
  }

  /** Waits for both halves, so the listing finds in the container what the live copy shows. */
  private fun grant(itemId: Long, amount: Int) {
    val durableBefore = durableAmount(itemId)
    world.modify(masterEntity()) { id -> add(id, ObtainItemIntent.CreateItemIntent(itemId, amount)) }

    await { assertTrue(durableAmount(itemId) >= durableBefore + amount) }
  }

  private fun liveAmount(itemId: Long): Int {
    return world.read {
      get(masterEntity(), Inventory::class)?.getItems()?.filter { it.itemId == itemId }?.sumOf { it.amount } ?: 0
    }
  }

  /** The pile or copy of [itemId] the player would drag into the window: the first one held. */
  private fun heldPileOf(itemId: Long): Long {
    return world.read { get(masterEntity(), Inventory::class)!!.getItems().first { it.itemId == itemId }.playerItemId }
  }

  private fun durableAmount(itemId: Long): Int {
    return TransactionTemplate(transactionManager).execute {
      masterRepository.findByIdOrThrow(connectionInfoService.getMasterId(player)).container.slots
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
