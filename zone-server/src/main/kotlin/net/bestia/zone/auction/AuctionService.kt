package net.bestia.zone.auction

import net.bestia.zone.skill.BasicSkillGate
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.auction.persistence.AuctionListing
import net.bestia.zone.auction.persistence.AuctionListingRepository
import net.bestia.zone.auction.persistence.PlayerAuctionHouseRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.entity.GameMetrics.CoinSource
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.grantToLive
import net.bestia.zone.item.ecs.takeFromLive
import net.bestia.zone.item.persistence.Item
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.postal.parcel.ParcelService
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration

/**
 * The markets: the start towns' shared one, and each player house with the houses of its owner. Off the tick:
 * every request reads or writes the database, and the world is only borrowed for the reach check, the live mirror
 * and the reserve.
 *
 * Goods move in the database first and are mirrored into the live inventory after the commit, as in a trade.
 */
@Service
class AuctionService(
  private val world: WorldView,
  private val connectionInfoService: ConnectionInfoService,
  private val houses: AuctionHouses,
  private val playerHouses: PlayerHouses,
  private val houseRows: PlayerAuctionHouseRepository,
  private val listings: AuctionListingRepository,
  private val parcels: ParcelService,
  private val inventoryService: InventoryService,
  private val itemRepository: ItemRepository,
  private val masterRepository: MasterRepository,
  private val commodityItems: CommodityItems,
  private val reserve: WorldReserve,
  private val outMessageProcessor: OutMessageProcessor,
  private val transactions: TransactionTemplate,
  private val itemTemplates: ItemTemplateRegistry,
  private val clock: Clock = Clock.systemUTC(),
  private val basicSkill: BasicSkillGate? = null,
) {

  fun list(accountId: AccountId, houseEntityId: Long, playerItemId: Long, amount: Int, terms: ListingTerms) {
    if (basicSkill?.mayUseTradePosts(accountId) == false) return deny(accountId, OpError.BASIC_SKILL_TRADE_POST_LOCKED)
    val price = terms.price
    val masterId = connectionInfoService.getMasterId(accountId)
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(accountId)
    val at = openHouseInReach(accountId, houseEntityId) ?: return
    val coinId = commodityItems.coinItemId() ?: return deny(accountId, OpError.REQUEST_REFUSED)

    // A client only offers what these allow, so a refusal here is a hand-made packet.
    val held = world.read {
      val worn = get(masterEntityId, Equipment::class)
      get(masterEntityId, Inventory::class)?.byPlayerItemId(playerItemId)
        ?.takeUnless { it.equipped || worn?.isWorn(it.uniqueId) == true }
    }
    val isPossible = held != null && held.itemId != coinId && amount in 1..held.amount && price in 1..MAX_PRICE && terms.isValid
    if (held == null || !isPossible) {
      LOG.warn { "Master $masterId sent an impossible listing: item $playerItemId x$amount for $price" }
      return deny(accountId, OpError.REQUEST_REFUSED)
    }
    if (itemTemplates.isBound(held.itemId)) {
      return deny(accountId, OpError.ITEM_BOUND)
    }

    if (listings.countBySellerMasterId(masterId) >= MAX_LISTINGS_PER_MASTER) {
      return deny(accountId, OpError.AUCTION_LISTING_LIMIT)
    }

    val listingFee = AuctionFees.listingFee(price, terms.days)
    val ownerFee = AuctionFees.ownerFee(price, (at as? AuctionHouses.At.Player)?.house?.feePercent ?: 0)
    val fee = listingFee + ownerFee
    val listed = transactions.execute { status ->
      escrow(masterId, held, amount, terms, fee, coinId, at).also { if (it is Refused) status.setRollbackOnly() }
    }

    if (listed !is Listed) {
      return deny(accountId, (listed as? Refused)?.code ?: OpError.REQUEST_REFUSED)
    }

    // Past the commit. A live inventory that disagrees is corrected by the next login; the database decided.
    if (!world.takeFromLive(masterEntityId, listed.goods)) {
      LOG.warn { "Master $masterId listed item ${held.itemId} that its live inventory does not hold" }
    }
    world.takeFromLive(masterEntityId, ReservedItem.stack(coinId, fee.toInt(), weight = 0))
    val toReserve = fee - AuctionFees.tillShare(ownerFee)
    world.read { reserve.charge(-toReserve.toDouble(), CoinSource.AUCTION_FEE) }

    search(accountId, AuctionQuery(houseEntityId, null, 0, 0, emptyList(), mine = true, page = 0))
  }

  fun buy(accountId: AccountId, houseEntityId: Long, listingId: Long) {
    if (basicSkill?.mayUseTradePosts(accountId) == false) return deny(accountId, OpError.BASIC_SKILL_TRADE_POST_LOCKED)
    val buyerId = connectionInfoService.getMasterId(accountId)
    val at = openHouseInReach(accountId, houseEntityId) ?: return
    val coinId = commodityItems.coinItemId() ?: return deny(accountId, OpError.REQUEST_REFUSED)

    val bought = transactions.execute { status ->
      purchase(buyerId, listingId, coinId, at).also { if (it is Refused) status.setRollbackOnly() }
    }

    if (bought !is Bought) {
      return deny(accountId, (bought as? Refused)?.code ?: OpError.REQUEST_REFUSED)
    }

    val buyerEntityId = connectionInfoService.getSelectedMasterEntityId(accountId)
    world.grantToLive(buyerEntityId, bought.goods)
    world.takeFromLive(buyerEntityId, ReservedItem.stack(coinId, bought.price.toInt(), weight = 0))

    val goods = bought.goods.first()
    outMessageProcessor.sendToPlayer(accountId, AuctionPurchasedSMSG(listingId, goods.itemId, goods.amount))
  }

  /** The goods come back; the fee does not, as the docs say of any listing that did not sell. */
  fun takeDown(accountId: AccountId, houseEntityId: Long, listingId: Long) {
    val sellerId = connectionInfoService.getMasterId(accountId)
    houseInReach(accountId, houseEntityId) ?: return deny(accountId, OpError.AUCTION_OUT_OF_RANGE)

    val outcome = transactions.execute {
      val listing = listings.findLockedById(listingId)?.takeIf { it.sellerMasterId == sellerId }
        ?: return@execute Refused(OpError.AUCTION_LISTING_GONE)
      if (listing.currentBid != null) return@execute Refused(OpError.AUCTION_HAS_BIDS)
      val goods = inventoryService.moveFromEscrow(listing.container, sellerId)
      listings.delete(listing)
      Bought(goods, price = 0)
    }
    if (outcome !is Bought) return deny(accountId, (outcome as? Refused)?.code ?: OpError.REQUEST_REFUSED)

    world.grantToLive(connectionInfoService.getSelectedMasterEntityId(accountId), outcome.goods)

    search(accountId, AuctionQuery(houseEntityId, null, 0, 0, emptyList(), mine = true, page = 0))
  }

  fun search(accountId: AccountId, query: AuctionQuery) {
    val at = openHouseInReach(accountId, query.houseEntityId) ?: return
    val shown = shownAt(at)

    val seller = if (query.mine) connectionInfoService.getMasterId(accountId) else null
    val itemIds = query.itemIds.take(MAX_ITEM_IDS)

    val page = transactions.execute {
      val found = listings.search(
        category = query.category,
        minLevel = query.minLevel.coerceAtLeast(0),
        maxLevel = if (query.maxLevel <= 0) Int.MAX_VALUE else query.maxLevel,
        anyItem = itemIds.isEmpty(),
        itemIds = itemIds.ifEmpty { listOf(NO_ITEM) },
        sellerMasterId = seller,
        now = clock.instant(),
        startTowns = shown.startTowns,
        houseIds = shown.houseIds.ifEmpty { listOf(NO_HOUSE) },
        pageable = PageRequest.of(query.page.coerceAtLeast(0), PAGE_SIZE),
      )

      val viewerId = connectionInfoService.getMasterId(accountId)
      AuctionListingsSMSG(found.number, found.totalPages, query.mine, found.content.map { rowOf(it, viewerId) })
    }

    page?.let { outMessageProcessor.sendToPlayer(accountId, it) }
  }

  private fun escrow(
    masterId: Long, held: Inventory.Item, amount: Int, terms: ListingTerms, fee: Long, coinId: Long, at: AuctionHouses.At,
  ): Outcome {
    val price = terms.price
    val itemId = held.itemId
    val item = itemRepository.findByIdOrNull(itemId) ?: return Refused(OpError.REQUEST_REFUSED)

    if (!inventoryService.consumeAll(masterId, listOf(coinId to fee.toInt()))) {
      return Refused(OpError.AUCTION_CANNOT_AFFORD_FEE)
    }

    val container = ItemContainer(ItemContainer.Type.STORAGE)
    val goods = inventoryService.moveToEscrow(masterId, itemId, held.uniqueId, amount, container, held.provenance)
      ?: return Refused(OpError.REQUEST_REFUSED)

    listings.save(
      AuctionListing(
        sellerMasterId = masterId,
        sellerName = masterRepository.findByIdOrThrow(masterId).name,
        itemId = itemId,
        amount = goods.amount,
        itemLevel = goods.itemLevel,
        category = AuctionCategory.of(item),
        price = price,
        townName = at.townName,
        houseStructureId = (at as? AuctionHouses.At.Player)?.house?.structureId,
        expiresAt = clock.instant().plus(if (terms.isAuction) Duration.ofDays(terms.days.toLong()) else FIXED_PRICE_DURATION),
        container = container,
        isAuction = terms.isAuction,
        buyout = terms.buyout,
      )
    )

    // The owner's half of the owner fee waits in the house's till; the rest of the fee goes to the reserve.
    if (at is AuctionHouses.At.Player) {
      val ownerFee = AuctionFees.ownerFee(price, at.house.feePercent)
      houseRows.findLockedById(at.house.structureId)?.let { it.till += AuctionFees.tillShare(ownerFee) }
    }

    return Listed(goods)
  }

  /**
   * Bids on a timed auction. The coin leaves the bidder's bag at once and the house holds it; the bid it beats goes
   * back to its bidder by post. A bid of the buyout ends the auction there and then.
   */
  fun bid(accountId: AccountId, houseEntityId: Long, listingId: Long, amount: Long) {
    val bidderId = connectionInfoService.getMasterId(accountId)
    val at = openHouseInReach(accountId, houseEntityId) ?: return
    val coinId = commodityItems.coinItemId() ?: return deny(accountId, OpError.REQUEST_REFUSED)

    val outcome = transactions.execute { status ->
      placeBid(bidderId, listingId, amount, coinId, at).also { if (it is Refused) status.setRollbackOnly() }
    }
    if (outcome !is BidPlaced) {
      deny(accountId, (outcome as? Refused)?.code ?: OpError.REQUEST_REFUSED)
      return search(accountId, AuctionQuery(houseEntityId, null, 0, 0, emptyList(), mine = false, page = 0))
    }

    world.takeFromLive(connectionInfoService.getSelectedMasterEntityId(accountId), ReservedItem.stack(coinId, outcome.paid.toInt(), weight = 0))
    search(accountId, AuctionQuery(houseEntityId, null, 0, 0, emptyList(), mine = false, page = 0))
  }

  private fun placeBid(bidderId: Long, listingId: Long, amount: Long, coinId: Long, at: AuctionHouses.At): Outcome {
    val listing = listings.findLockedById(listingId)
      ?.takeIf { it.isAuction && shownAt(at).shows(it) && it.expiresAt.isAfter(clock.instant()) }
      ?: return Refused(OpError.AUCTION_LISTING_GONE)
    if (listing.sellerMasterId == bidderId || listing.bidderMasterId == bidderId) return Refused(OpError.REQUEST_REFUSED)

    val bid = listing.buyout?.let { minOf(amount, it) } ?: amount
    if (bid < listing.nextMinBid || bid > MAX_PRICE) return Refused(OpError.AUCTION_BID_TOO_LOW)

    if (!inventoryService.consumeAll(bidderId, listOf(coinId to bid.toInt()))) {
      return Refused(OpError.AUCTION_CANNOT_AFFORD)
    }

    val coin = itemRepository.findByIdOrNull(coinId) ?: return Refused(OpError.REQUEST_REFUSED)
    val outbid = listing.bidderMasterId
    val outbidAmount = listing.currentBid
    if (outbid != null && outbidAmount != null) {
      parcels.postInstant(outbid, SENDER_NAME) { it.addStackable(coin, outbidAmount.toInt()) }
    }

    listing.currentBid = bid
    listing.bidderMasterId = bidderId
    if (bid == listing.buyout) settle(listing, coin)

    return BidPlaced(bid)
  }

  /**
   * Listings whose time ran out: an auction with a bid goes to its highest bidder, everything else goes back to its
   * seller's mailbox. The fee does not come back, as the docs say of any listing that did not sell.
   */
  @Scheduled(fixedDelay = 60 * 1000L)
  fun returnExpired() {
    transactions.execute {
      for (listing in listings.findByExpiresAtBefore(clock.instant())) {
        // Asked only once something is due: the first run comes before the items are imported, and the coin's id
        // is resolved once, for good.
        val coin = listing.bidderMasterId?.let { commodityItems.coinItemId() }?.let { itemRepository.findByIdOrNull(it) }
        if (coin != null) {
          settle(listing, coin)
          continue
        }
        parcels.postInstant(listing.sellerMasterId, SENDER_NAME) { parcel ->
          inventoryService.moveBetweenEscrows(listing.container, parcel)
        }
        listings.delete(listing)
      }
    }
  }

  /** Inside a transaction: the winner gets the goods and the seller the winning bid, both by post. */
  private fun settle(listing: AuctionListing, coin: Item) {
    val winner = listing.bidderMasterId ?: return
    val bid = listing.currentBid ?: return

    parcels.postInstant(winner, SENDER_NAME) { parcel -> inventoryService.moveBetweenEscrows(listing.container, parcel) }
    parcels.postInstant(listing.sellerMasterId, SENDER_NAME) { it.addStackable(coin, bid.toInt()) }
    listings.delete(listing)
  }

  /** The seller is paid by post: they are rarely standing at a house when somebody buys. */
  private fun purchase(buyerId: Long, listingId: Long, coinId: Long, at: AuctionHouses.At): Outcome {
    val listing = listings.findLockedById(listingId)
      ?.takeIf { shownAt(at).shows(it) }
      ?: return Refused(OpError.AUCTION_LISTING_GONE)

    if (listing.sellerMasterId == buyerId || listing.isAuction) {
      return Refused(OpError.REQUEST_REFUSED)
    }

    if (!inventoryService.consumeAll(buyerId, listOf(coinId to listing.price.toInt()))) {
      return Refused(OpError.AUCTION_CANNOT_AFFORD)
    }

    val goods = inventoryService.moveFromEscrow(listing.container, buyerId)

    val coin = itemRepository.findByIdOrNull(coinId) ?: return Refused(OpError.REQUEST_REFUSED)
    parcels.postInstant(listing.sellerMasterId, SENDER_NAME) { it.addStackable(coin, listing.price.toInt()) }
    listings.delete(listing)

    return Bought(goods, listing.price)
  }

  private fun rowOf(listing: AuctionListing, viewerId: Long): AuctionListingsSMSG.Row {
    return AuctionListingsSMSG.Row(
      listingId = listing.id,
      goods = ReservedItem.of(listing.container.slots.single(), listing.itemLevel),
      price = listing.price,
      sellerName = listing.sellerName,
      townName = listing.townName,
      expiresAt = listing.expiresAt,
      bid = if (listing.isAuction) {
        AuctionListingsSMSG.Bid(listing.currentBid ?: 0, listing.nextMinBid, listing.buyout ?: 0, listing.bidderMasterId == viewerId)
      } else {
        null
      },
    )
  }

  private fun houseInReach(accountId: AccountId, houseEntityId: Long): AuctionHouses.At? {
    val actorId = connectionInfoService.getActiveEntityId(accountId)

    return world.read { houses.houseInReach(this, actorId, houseEntityId) }
  }

  /** The house the request is made at, if it is in reach and open; otherwise the player is told why not. */
  private fun openHouseInReach(accountId: AccountId, houseEntityId: Long): AuctionHouses.At? {
    val at = houseInReach(accountId, houseEntityId)
    if (at == null) {
      deny(accountId, OpError.AUCTION_OUT_OF_RANGE)
      return null
    }
    if (at is AuctionHouses.At.Player && !at.isOpen) {
      deny(accountId, OpError.AUCTION_HOUSE_CLOSED)
      return null
    }

    return at
  }

  /** Which listings a house shows: the start towns' shared market, or the player houses it shares with. */
  private data class Shown(val startTowns: Boolean, val houseIds: Set<Long>) {
    fun shows(listing: AuctionListing): Boolean {
      val house = listing.houseStructureId

      return if (house == null) startTowns else house in houseIds
    }
  }

  private fun shownAt(at: AuctionHouses.At): Shown {
    return when (at) {
      is AuctionHouses.At.StartTown -> Shown(startTowns = true, houseIds = emptySet())
      is AuctionHouses.At.Player -> Shown(startTowns = false, houseIds = playerHouses.sharingWith(at.house))
    }
  }

  private fun deny(accountId: AccountId, code: OpError) {
    outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(code))
  }

  private sealed interface Outcome
  private data class Listed(val goods: ReservedItem) : Outcome
  private data class Bought(val goods: List<ReservedItem>, val price: Long) : Outcome
  private data class Refused(val code: OpError) : Outcome
  private data class BidPlaced(val paid: Long) : Outcome

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** Who auction mail is from, as the mailbox shows it. */
    const val SENDER_NAME = "Auction House"

    /** The docs give fixed-price listings no length; the longest auction's keeps the board from silting up. */
    val FIXED_PRICE_DURATION: Duration = Duration.ofDays(5)

    const val MAX_LISTINGS_PER_MASTER = 20
    const val PAGE_SIZE = 20

    /** Ten percent of it still fits the coin stack a purse can pay from. */
    const val MAX_PRICE = 1_000_000_000L

    /** What a client resolves a typed name to. More is a name that matches everything. */
    const val MAX_ITEM_IDS = 200

    /** Fills the `IN` list when any item will do; no item has this id. */
    private const val NO_ITEM = -1L

    /** Fills the houses' `IN` list at a start town; no structure has this id. */
    private const val NO_HOUSE = -1L
  }
}
