package net.bestia.zone.scenarios

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.auction.AuctionFees
import net.bestia.zone.auction.ListingTerms
import net.bestia.zone.auction.AuctionListingsSMSG
import net.bestia.zone.auction.AuctionPurchasedSMSG
import net.bestia.zone.auction.PlayerHouses
import net.bestia.zone.auction.TradeLinks
import net.bestia.zone.auction.net.BuyAuctionListingCMSG
import net.bestia.zone.auction.net.PostPickupCMSG
import net.bestia.zone.auction.persistence.AuctionPickupRepository
import net.bestia.zone.auction.net.ListAuctionItemCMSG
import net.bestia.zone.auction.persistence.AuctionListingRepository
import net.bestia.zone.auction.persistence.PlayerAuctionHouseRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PlayerStructureIdentity
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ObtainItemIntent
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.item.persistence.findByIdentifierOrThrow
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.postal.persistence.ParcelRepository
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.settlement.TownFellEvent
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.settlement.persistence.PlayerSettlement
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A player's auction house in a player town, over the real message path: its owner fee, its till, its closing. */
class PlayerAuctionHouseScenarios : BestiaNoSocketScenario() {

  @Autowired private lateinit var world: WorldView
  @Autowired private lateinit var connectionInfoService: ConnectionInfoService
  @Autowired private lateinit var towns: TownRegistry
  @Autowired private lateinit var structures: PlayerStructureRegistry
  @Autowired private lateinit var playerHouses: PlayerHouses
  @Autowired private lateinit var houseRows: PlayerAuctionHouseRepository
  @Autowired private lateinit var listings: AuctionListingRepository
  @Autowired private lateinit var parcels: ParcelRepository
  @Autowired private lateinit var itemRepository: ItemRepository
  @Autowired private lateinit var commodityItems: CommodityItems
  @Autowired private lateinit var reserve: WorldReserve
  @Autowired private lateinit var events: ApplicationEventPublisher
  @Autowired private lateinit var transactionManager: PlatformTransactionManager
  @Autowired private lateinit var masterRepository: MasterRepository
  @Autowired private lateinit var links: TradeLinks
  @Autowired private lateinit var pickups: AuctionPickupRepository

  private val player get() = clientPlayer1.connectedPlayerId

  private var townId = 0L
  private var structureId = 0L
  private var house: EntityId = 0L

  private val buyer get() = clientPlayer2.connectedPlayerId
  private var buyersStructureId = 0L
  private var buyersHouse: EntityId = 0L

  @BeforeAll
  fun buildTownAndHouse() {
    val masterId = connectionInfoService.getMasterId(player)
    val at = world.read { get(masterEntity(), Position::class)!!.toVec3L() }

    // From the test thread: the tick may not write rows, and nothing on it reads these until the house exists.
    val town = towns.create(SITE_ID, "Scenario Hold", masterId, player, "Ada", at, Instant.now())
    towns.update(town.copy(state = PlayerSettlement.State.STANDING, siteStructureId = null))
    townId = town.id
    structureId = structures.place(StaticEntityKind.AUCTION_HOUSE, masterId, player, at, 0f, 0, 0).id
    playerHouses.register(structureId, masterId, player, FEE_PERCENT)
    await { assertTrue(houseRows.findByIdOrNull(structureId) != null) }

    // The buyer runs a house of their own, at the same town, which is linked to the seller's below.
    val buyerMasterId = connectionInfoService.getMasterId(buyer)
    val buyerAt = world.read { get(connectionInfoService.getSelectedMasterEntityId(buyer), Position::class)!!.toVec3L() }
    buyersStructureId = structures.place(StaticEntityKind.AUCTION_HOUSE, buyerMasterId, buyer, buyerAt, 0f, 0, 0).id
    playerHouses.register(buyersStructureId, buyerMasterId, buyer, FEE_PERCENT)
    await { assertTrue(houseRows.findByIdOrNull(buyersStructureId) != null) }
    buyersHouse = world.read {
      createEntity { id ->
        add(id, PropPose(buyerAt, yaw = 0f))
        add(id, PlayerStructureIdentity(buyersStructureId, buyer))
        add(id, StaticVisual(StaticEntityKind.AUCTION_HOUSE, variant = 0, heightDm = 40, halfLengthDm = 60, halfWidthDm = 40))
      }
    }

    house = world.read {
      createEntity { id ->
        add(id, PropPose(at, yaw = 0f))
        add(id, PlayerStructureIdentity(structureId, player))
        add(id, StaticVisual(StaticEntityKind.AUCTION_HOUSE, variant = 0, heightDm = 40, halfLengthDm = 60, halfWidthDm = 40))
      }
    }
  }

  @AfterEach
  fun clearListings() {
    TransactionTemplate(transactionManager).execute {
      listings.deleteAll()
      parcels.deleteAll()
      pickups.deleteAll()
      houseRows.findByIdOrNull(structureId)?.let { it.till = 0 }
    }
  }

  @AfterAll
  fun removeTownAndHouse() {
    world.modify(house) { id -> destroy(id) }
    world.modify(buyersHouse) { id -> destroy(id) }
    towns.remove(townId)
    structures.remove(structureId, 0, 0)
    structures.remove(buyersStructureId, 0, 0)
    playerHouses.forget(structureId)
    playerHouses.forget(buyersStructureId)
    links.unlink(connectionInfoService.getMasterId(player), connectionInfoService.getMasterId(buyer))
    TransactionTemplate(transactionManager).execute {
      houseRows.deleteById(structureId)
      houseRows.deleteById(buyersStructureId)
    }
  }

  @Test
  fun `a listing pays the owner fee on top, and half of it waits in the till`() {
    val price = 1_000L
    val ownerFee = AuctionFees.ownerFee(price, FEE_PERCENT)
    val fee = AuctionFees.listingFee(price) + ownerFee
    val reserveBefore = world.read { reserve.available() }

    listApples(price, fee)

    val listing = listings.findAll().single()
    assertEquals(structureId, listing.houseStructureId)
    assertEquals(AuctionFees.tillShare(ownerFee), houseRows.findByIdOrNull(structureId)!!.till)
    assertEquals(reserveBefore + fee - AuctionFees.tillShare(ownerFee), world.read { reserve.available() }, 1e-9)
  }

  @Test
  fun `a fallen town closes the house, and its listings and till go home by post`() {
    listApples(1_000L, AuctionFees.listingFee(1_000L) + AuctionFees.ownerFee(1_000L, FEE_PERCENT))

    world.post { events.publishEvent(TownFellEvent(this, townId, "Scenario Hold", towns.of(townId)!!.sign)) }

    await {
      assertEquals(0L, listings.count())
      assertEquals(2L, parcels.count(), "the goods, and the till")
      assertEquals(0L, houseRows.findByIdOrNull(structureId)!!.till)
    }
  }

  @Test
  fun `a purchase from a linked house waits there, and the buyer can have it posted home`() {
    links.link(
      TradeLinks.Partner(connectionInfoService.getMasterId(player), "Seller"),
      TradeLinks.Partner(connectionInfoService.getMasterId(buyer), "Buyer"),
    )
    listApples(100L, AuctionFees.listingFee(100L) + AuctionFees.ownerFee(100L, FEE_PERCENT))
    val listingId = listings.findAll().single().id
    grant(commodityItems.coinItemId()!!, 200, buyer)

    clientPlayer2.sendMessage(BuyAuctionListingCMSG(buyer, buyersHouse, listingId))

    val purchased = awaitValue { assertNotNull(clientPlayer2.tryGetLastReceived(AuctionPurchasedSMSG::class)) }
    assertEquals("Scenario Hold", purchased.waitingAt)
    val pickupId = pickups.findAll().single().id

    clientPlayer2.sendMessage(PostPickupCMSG(buyer, pickupId))

    await { assertEquals(0L, pickups.count()) }
    val buyerMasterId = connectionInfoService.getMasterId(buyer)
    assertTrue(TransactionTemplate(transactionManager).execute { parcels.findByRecipientMasterIdOrderByLatestArrival(buyerMasterId).isNotEmpty() }!!)
  }

  private fun listApples(price: Long, fee: Long) {
    val apple = itemRepository.findByIdentifierOrThrow("apple").id
    grant(apple, 2)
    grant(commodityItems.coinItemId()!!, fee.toInt())
    clientPlayer1.clearMessages()

    clientPlayer1.sendMessage(ListAuctionItemCMSG(player, house, heldPileOf(apple, 2), amount = 2, terms = ListingTerms(price)))

    await {
      val page = assertNotNull(clientPlayer1.tryGetLastReceived(AuctionListingsSMSG::class))
      assertTrue(page.mine && page.listings.isNotEmpty())
    }
  }

  private fun masterEntity(): EntityId {
    return connectionInfoService.getSelectedMasterEntityId(player)
  }

  /** Waits for the durable half, so the listing finds in the container what the live copy shows. */
  private fun grant(itemId: Long, amount: Int, accountId: Long = player) {
    val before = durableAmount(itemId, accountId)
    world.modify(connectionInfoService.getSelectedMasterEntityId(accountId)) { id ->
      add(id, ObtainItemIntent.CreateItemIntent(itemId, amount))
    }

    await { assertTrue(durableAmount(itemId, accountId) >= before + amount) }
  }

  private fun <T : Any> awaitValue(fn: () -> T): T {
    var result: T? = null
    await { result = fn() }
    return result!!
  }

  private fun durableAmount(itemId: Long, accountId: Long = player): Int {
    return TransactionTemplate(transactionManager).execute {
      masterRepository.findByIdOrThrow(connectionInfoService.getMasterId(accountId)).container.slots
        .filter { it.isFree && it.template.id == itemId }
        .sumOf { it.amount }
    }!!
  }

  private fun heldPileOf(itemId: Long, amount: Int): Long {
    return world.read {
      get(masterEntity(), Inventory::class)!!.getItems().first { it.itemId == itemId && it.amount >= amount }.playerItemId
    }
  }

  private companion object {
    const val FEE_PERCENT = 10

    /** A town is named by its sign site's structure id; no row needs to exist for one made here. */
    const val SITE_ID = 990_001L
  }
}
