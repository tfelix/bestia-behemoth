package net.bestia.zone.auction

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.auction.persistence.AuctionClaim
import net.bestia.zone.auction.persistence.AuctionClaimRepository
import net.bestia.zone.auction.persistence.AuctionListing
import net.bestia.zone.auction.persistence.AuctionListingRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.grantToLive
import net.bestia.zone.item.ecs.takeFromLive
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration

/**
 * The shared market of the start towns. Off the tick: every request reads or writes the database, and the
 * world is only borrowed for the reach check, the live mirror and the reserve.
 *
 * Goods move in the database first and are mirrored into the live inventory after the commit, as in a trade.
 */
@Service
class AuctionService(
  private val world: WorldView,
  private val connectionInfoService: ConnectionInfoService,
  private val houses: AuctionHouses,
  private val listings: AuctionListingRepository,
  private val claims: AuctionClaimRepository,
  private val inventoryService: InventoryService,
  private val itemRepository: ItemRepository,
  private val masterRepository: MasterRepository,
  private val commodityItems: CommodityItems,
  private val reserve: WorldReserve,
  private val outMessageProcessor: OutMessageProcessor,
  private val transactions: TransactionTemplate,
  private val clock: Clock = Clock.systemUTC(),
) {

  fun list(accountId: AccountId, houseEntityId: Long, playerItemId: Long, amount: Int, price: Long) {
    val masterId = connectionInfoService.getMasterId(accountId)
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(accountId)
    val town = townInReach(accountId, houseEntityId) ?: return deny(accountId, OpError.AUCTION_OUT_OF_RANGE)
    val coinId = commodityItems.coinItemId() ?: return deny(accountId, OpError.REQUEST_REFUSED)

    // A client only offers what these allow, so a refusal here is a hand-made packet.
    val held = world.read {
      val worn = get(masterEntityId, Equipment::class)
      get(masterEntityId, Inventory::class)?.byPlayerItemId(playerItemId)
        ?.takeUnless { it.equipped || worn?.isWorn(it.uniqueId) == true }
    }
    if (held == null || held.itemId == coinId || amount <= 0 || held.amount < amount || price <= 0 || price > MAX_PRICE) {
      LOG.warn { "Master $masterId sent an impossible listing: item $playerItemId x$amount for $price" }
      return deny(accountId, OpError.REQUEST_REFUSED)
    }

    if (listings.countBySellerMasterId(masterId) >= MAX_LISTINGS_PER_MASTER) {
      return deny(accountId, OpError.AUCTION_LISTING_LIMIT)
    }

    val fee = AuctionFees.listingFee(price)
    val listed = transactions.execute { status ->
      escrow(masterId, held, amount, price, fee, coinId, town).also { if (it is Refused) status.setRollbackOnly() }
    }

    if (listed !is Listed) {
      return deny(accountId, (listed as? Refused)?.code ?: OpError.REQUEST_REFUSED)
    }

    // Past the commit. A live inventory that disagrees is corrected by the next login; the database decided.
    if (!world.takeFromLive(masterEntityId, listed.goods)) {
      LOG.warn { "Master $masterId listed item ${held.itemId} that its live inventory does not hold" }
    }
    world.takeFromLive(masterEntityId, ReservedItem.stack(coinId, fee.toInt(), weight = 0))
    world.read { reserve.charge(-fee.toDouble()) }

    search(accountId, AuctionQuery(houseEntityId, null, 0, 0, emptyList(), mine = true, page = 0))
  }

  fun buy(accountId: AccountId, houseEntityId: Long, listingId: Long) {
    val buyerId = connectionInfoService.getMasterId(accountId)
    townInReach(accountId, houseEntityId) ?: return deny(accountId, OpError.AUCTION_OUT_OF_RANGE)
    val coinId = commodityItems.coinItemId() ?: return deny(accountId, OpError.REQUEST_REFUSED)

    val bought = transactions.execute { status ->
      purchase(buyerId, listingId, coinId).also { if (it is Refused) status.setRollbackOnly() }
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
    townInReach(accountId, houseEntityId) ?: return deny(accountId, OpError.AUCTION_OUT_OF_RANGE)

    val returned = transactions.execute {
      val listing = listings.findLockedById(listingId)?.takeIf { it.sellerMasterId == sellerId }
        ?: return@execute null
      val goods = inventoryService.moveFromEscrow(listing.container, sellerId)
      listings.delete(listing)
      goods
    } ?: return deny(accountId, OpError.AUCTION_LISTING_GONE)

    world.grantToLive(connectionInfoService.getSelectedMasterEntityId(accountId), returned)

    search(accountId, AuctionQuery(houseEntityId, null, 0, 0, emptyList(), mine = true, page = 0))
  }

  fun search(accountId: AccountId, query: AuctionQuery) {
    townInReach(accountId, query.houseEntityId) ?: return deny(accountId, OpError.AUCTION_OUT_OF_RANGE)

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
        pageable = PageRequest.of(query.page.coerceAtLeast(0), PAGE_SIZE),
      )

      AuctionListingsSMSG(found.number, found.totalPages, query.mine, found.content.map(::rowOf))
    }

    page?.let { outMessageProcessor.sendToPlayer(accountId, it) }
  }

  private fun escrow(
    masterId: Long, held: Inventory.Item, amount: Int, price: Long, fee: Long, coinId: Long, town: String,
  ): Outcome {
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
        townName = town,
        expiresAt = clock.instant().plus(FIXED_PRICE_DURATION),
        container = container,
      )
    )

    return Listed(goods)
  }

  /** The seller is paid through a claim: they are rarely standing at a house when somebody buys. */
  private fun purchase(buyerId: Long, listingId: Long, coinId: Long): Outcome {
    val listing = listings.findLockedById(listingId) ?: return Refused(OpError.AUCTION_LISTING_GONE)

    if (listing.sellerMasterId == buyerId) {
      return Refused(OpError.REQUEST_REFUSED)
    }

    if (!inventoryService.consumeAll(buyerId, listOf(coinId to listing.price.toInt()))) {
      return Refused(OpError.AUCTION_CANNOT_AFFORD)
    }

    val goods = inventoryService.moveFromEscrow(listing.container, buyerId)

    claims.save(
      AuctionClaim(
        masterId = listing.sellerMasterId,
        reason = AuctionClaim.Reason.SOLD,
        coins = listing.price,
        itemId = listing.itemId,
        amount = listing.amount,
        createdAt = clock.instant(),
      )
    )
    listings.delete(listing)

    return Bought(goods, listing.price)
  }

  private fun rowOf(listing: AuctionListing): AuctionListingsSMSG.Row {
    return AuctionListingsSMSG.Row(
      listingId = listing.id,
      goods = ReservedItem.of(listing.container.slots.single(), listing.itemLevel),
      price = listing.price,
      sellerName = listing.sellerName,
      townName = listing.townName,
      expiresAt = listing.expiresAt,
    )
  }

  private fun townInReach(accountId: AccountId, houseEntityId: Long): String? {
    val actorId = connectionInfoService.getActiveEntityId(accountId)

    return world.read { houses.townInReach(this, actorId, houseEntityId) }
  }

  private fun deny(accountId: AccountId, code: OpError) {
    outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(code))
  }

  private sealed interface Outcome
  private data class Listed(val goods: ReservedItem) : Outcome
  private data class Bought(val goods: List<ReservedItem>, val price: Long) : Outcome
  private data class Refused(val code: OpError) : Outcome

  companion object {
    private val LOG = KotlinLogging.logger { }

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
  }
}
