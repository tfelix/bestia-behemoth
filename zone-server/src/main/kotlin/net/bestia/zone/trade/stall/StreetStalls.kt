package net.bestia.zone.trade.stall

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.casting.StallPitching
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.ecs.Inventory
import net.bestia.zone.item.ecs.ItemTemplateRegistry
import net.bestia.zone.item.ecs.grantToLive
import net.bestia.zone.item.ecs.takeFromLive
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.postal.parcel.ParcelService
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.skill.BasicSkillGate
import net.bestia.zone.trade.persistence.StreetStallListing
import net.bestia.zone.trade.persistence.StreetStallListingRepository
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Street stalls: a Trader puts one down, lists goods from the bag on it, and other players buy them on the spot.
 *
 * Listed goods move into an escrow container per listing, as an auction listing's do, so they are in exactly one
 * place at any time. The stall itself is only a live entity; when it packs up, or the server restarts under it,
 * the goods go back to the seller.
 */
@Service
class StreetStalls(
  private val world: WorldView,
  private val listings: StreetStallListingRepository,
  private val inventoryService: InventoryService,
  private val itemTemplates: ItemTemplateRegistry,
  private val commodityItems: CommodityItems,
  private val parcels: ParcelService,
  private val connections: ConnectionInfoService,
  private val basicSkill: BasicSkillGate,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val transactions: TransactionTemplate,
  private val out: OutMessageProcessor,
) : StallPitching {

  override fun pitch(world: World, casterId: EntityId, maxListings: Int, seconds: Int): OpError? {
    val master = world.get(casterId, Master::class) ?: return OpError.REQUEST_REFUSED
    val accountId = world.get(casterId, Account::class)?.accountId ?: return OpError.REQUEST_REFUSED
    val at = world.get(casterId, Position::class)?.toVec3L() ?: return OpError.REQUEST_REFUSED
    if (stallOf(world, master.masterId) != null) return OpError.STALL_ALREADY_OPEN

    val stall = StreetStall(master.masterId, accountId, casterId, master.name, maxListings, at, seconds.toFloat())
    val stallId = world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, EntityVisual(VisualKind.STRUCTURE, StaticEntityKind.STREET_STALL.ordinal.toLong()))
      add(id, stall)
    }
    out.sendToPlayer(accountId, StreetStallSMSG(stallId, stall.sellerName, true, maxListings, seconds, false, emptyList()))

    return null
  }

  /** A click on the stall: the window, as its seller or as a buyer sees it. */
  fun view(world: World, viewerId: EntityId, stallId: EntityId): OpError? {
    val accountId = world.get(viewerId, Account::class)?.accountId ?: return null
    val stall = world.get(stallId, StreetStall::class) ?: return OpError.STALL_GONE
    if (!isInReach(world, viewerId, stall)) return OpError.STALL_OUT_OF_RANGE

    val mine = world.get(viewerId, Master::class)?.masterId == stall.sellerMasterId
    asyncJobExecutor.submit(stallId) { out.sendToPlayer(accountId, windowOf(stallId, stall, mine)) }

    return null
  }

  fun list(accountId: AccountId, stallId: EntityId, playerItemId: Long, amount: Int, price: Long) {
    val masterId = connections.getMasterId(accountId)
    val masterEntityId = connections.getSelectedMasterEntityId(accountId)
    val coinId = commodityItems.coinItemId() ?: return deny(accountId, OpError.REQUEST_REFUSED)

    val (stall, held) = world.read {
      val stall = get(stallId, StreetStall::class)?.takeIf { it.sellerMasterId == masterId }
      val worn = get(masterEntityId, Equipment::class)
      val held = get(masterEntityId, Inventory::class)?.byPlayerItemId(playerItemId)
        ?.takeUnless { it.equipped || worn?.isWorn(it.uniqueId) == true }
      stall to held
    }
    stall ?: return deny(accountId, OpError.STALL_GONE)
    if (held == null || held.itemId == coinId || amount <= 0 || held.amount < amount || price <= 0 || price > MAX_PRICE) {
      LOG.warn { "Master $masterId sent an impossible stall listing: item $playerItemId x$amount for $price" }
      return deny(accountId, OpError.REQUEST_REFUSED)
    }
    if (itemTemplates.isBound(held.itemId)) return deny(accountId, OpError.ITEM_BOUND)

    val goods = transactions.execute {
      if (listings.countByStallEntityId(stallId) >= stall.maxListings) return@execute null
      val container = ItemContainer(ItemContainer.Type.STORAGE)
      val moved = inventoryService.moveToEscrow(masterId, held.itemId, held.uniqueId, amount, container, held.provenance)
        ?: return@execute null
      listings.save(StreetStallListing(stallId, masterId, held.itemId, moved.amount, moved.itemLevel, price, container))
      moved
    } ?: return deny(accountId, OpError.STALL_FULL)

    world.takeFromLive(masterEntityId, goods)
    out.sendToPlayer(accountId, windowOf(stallId, stall, mine = true))
  }

  fun unlist(accountId: AccountId, stallId: EntityId, listingId: Long) {
    val masterId = connections.getMasterId(accountId)
    val stall = world.read { get(stallId, StreetStall::class) }?.takeIf { it.sellerMasterId == masterId }
      ?: return deny(accountId, OpError.STALL_GONE)

    val returned = transactions.execute {
      val listing = listings.findLockedById(listingId)?.takeIf { it.stallEntityId == stallId } ?: return@execute null
      val goods = inventoryService.moveFromEscrow(listing.container, masterId)
      listings.delete(listing)
      goods
    } ?: return deny(accountId, OpError.STALL_GONE)

    world.grantToLive(connections.getSelectedMasterEntityId(accountId), returned)
    out.sendToPlayer(accountId, windowOf(stallId, stall, mine = true))
  }

  /** The seller stands at the stall, so they are paid into the bag rather than by post. */
  fun buy(accountId: AccountId, stallId: EntityId, listingId: Long) {
    if (!basicSkill.mayUseTradePosts(accountId)) return deny(accountId, OpError.BASIC_SKILL_TRADE_POST_LOCKED)
    val buyerId = connections.getMasterId(accountId)
    val buyerEntityId = connections.getSelectedMasterEntityId(accountId)
    val coinId = commodityItems.coinItemId() ?: return deny(accountId, OpError.REQUEST_REFUSED)

    val stall = world.read {
      get(stallId, StreetStall::class)?.takeIf { isInReach(this, connections.getActiveEntityId(accountId), it) }
    } ?: return deny(accountId, OpError.STALL_OUT_OF_RANGE)
    if (stall.sellerMasterId == buyerId) return deny(accountId, OpError.REQUEST_REFUSED)

    val bought = transactions.execute { status ->
      purchase(buyerId, stallId, listingId, coinId, stall.sellerMasterId).also { if (it is Refused) status.setRollbackOnly() }
    }
    if (bought !is Bought) return deny(accountId, (bought as? Refused)?.code ?: OpError.REQUEST_REFUSED)

    val coins = ReservedItem.stack(coinId, bought.price.toInt(), weight = 0)
    world.grantToLive(buyerEntityId, bought.goods)
    world.takeFromLive(buyerEntityId, coins)
    world.grantToLive(stall.sellerEntityId, listOf(coins))

    out.sendToPlayer(accountId, windowOf(stallId, stall, mine = false))
    out.sendToPlayer(stall.sellerAccountId, windowOf(stallId, stall, mine = true))
  }

  /**
   * Packs the stall up: every unsold lot goes back into the seller's bag, or by post if they are gone. Off the tick,
   * then the stall leaves the world. The caller has already taken [StreetStall] off it, so it closes once.
   */
  fun close(stallId: EntityId, stall: StreetStall) {
    asyncJobExecutor.submit(stallId) {
      val returned = transactions.execute { returnAll(listings.findByStallEntityId(stallId)) }.orEmpty()
      if (connections.isPlaying(stall.sellerAccountId, stall.sellerMasterId)) {
        world.grantToLive(stall.sellerEntityId, returned)
      }
      world.post { if (isAlive(stallId)) destroy(stallId) }
      out.sendToPlayer(stall.sellerAccountId, StreetStallSMSG.closed(stallId))
    }
  }

  /** Stalls do not survive a restart, so whatever was listed on one when the server went down goes home by post. */
  @EventListener(ApplicationReadyEvent::class)
  fun returnLeftovers() {
    transactions.execute {
      for (listing in listings.findAll()) {
        parcels.postInstant(listing.sellerMasterId, SENDER_NAME) { parcel ->
          inventoryService.moveBetweenEscrows(listing.container, parcel)
        }
        listings.delete(listing)
      }
    }
  }

  private fun returnAll(stallListings: List<StreetStallListing>): List<ReservedItem> {
    return stallListings.flatMap { listing ->
      inventoryService.moveFromEscrow(listing.container, listing.sellerMasterId).also { listings.delete(listing) }
    }
  }

  private fun purchase(buyerId: Long, stallId: EntityId, listingId: Long, coinId: Long, sellerId: Long): Outcome {
    val listing = listings.findLockedById(listingId)?.takeIf { it.stallEntityId == stallId }
      ?: return Refused(OpError.STALL_GONE)
    if (!inventoryService.consumeAll(buyerId, listOf(coinId to listing.price.toInt()))) {
      return Refused(OpError.STALL_CANNOT_AFFORD)
    }

    val goods = inventoryService.moveFromEscrow(listing.container, buyerId)
    inventoryService.addItem(sellerId, CommodityItems.COIN, listing.price.toInt())
    listings.delete(listing)

    return Bought(goods, listing.price)
  }

  private fun windowOf(stallId: EntityId, stall: StreetStall, mine: Boolean): StreetStallSMSG {
    val rows = transactions.execute {
      listings.findByStallEntityId(stallId).map {
        StreetStallSMSG.Row(it.id, ReservedItem.of(it.container.slots.single(), it.itemLevel), it.price)
      }
    }.orEmpty()

    return StreetStallSMSG(
      stallId, stall.sellerName, mine, stall.maxListings, stall.remainingSeconds.toInt(), closed = false, rows = rows
    )
  }

  private fun isInReach(world: World, viewerId: EntityId, stall: StreetStall): Boolean {
    val at = world.get(viewerId, Position::class)?.toVec3L() ?: return false
    return at.distance(stall.at) <= REACH_TILES
  }

  private fun stallOf(world: World, masterId: Long): EntityId? {
    var found: EntityId? = null
    world.query(StreetStall::class).each { id -> if (get<StreetStall>().sellerMasterId == masterId) found = id }
    return found
  }

  private fun deny(accountId: AccountId, code: OpError) {
    out.sendToPlayer(accountId, OperationErrorSMSG(code))
  }

  private sealed interface Outcome
  private data class Bought(val goods: List<ReservedItem>, val price: Long) : Outcome
  private data class Refused(val code: OpError) : Outcome

  companion object {
    private val LOG = KotlinLogging.logger { }

    const val REACH_TILES = 3L

    /** How far the seller may step from their stall before it packs up. */
    const val WALK_AWAY_TILES = 5L

    private const val MAX_PRICE = 1_000_000_000L
    private const val SENDER_NAME = "Street Stall"
  }
}
