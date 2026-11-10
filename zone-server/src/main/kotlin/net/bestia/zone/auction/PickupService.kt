package net.bestia.zone.auction

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.auction.persistence.AuctionPickup
import net.bestia.zone.auction.persistence.AuctionPickupRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.economy.WorldReserve
import net.bestia.zone.entity.GameMetrics.CoinSource
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.ecs.ItemOwner
import net.bestia.zone.item.ecs.grantToLive
import net.bestia.zone.item.ecs.takeFromLive
import net.bestia.zone.item.persistence.ItemContainer
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.postal.office.PostOffices
import net.bestia.zone.postal.parcel.Freight
import net.bestia.zone.postal.parcel.ParcelService
import net.bestia.zone.postal.parcel.RoadDistance
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration

/**
 * Goods bought or won from a linked house wait at that house: the docs' Won & Bought window. The buyer collects
 * them in person, master or bestia, or pays the parcel fee to have them sent home. Off the tick.
 */
@Service
class PickupService(
  private val world: WorldView,
  private val connections: ConnectionInfoService,
  private val pickups: AuctionPickupRepository,
  private val structures: PlayerStructureRegistry,
  private val offices: PostOffices,
  private val roads: RoadDistance,
  private val masters: MasterRepository,
  private val inventoryService: InventoryService,
  private val parcels: ParcelService,
  private val commodityItems: CommodityItems,
  private val reserve: WorldReserve,
  private val out: OutMessageProcessor,
  private val transactions: TransactionTemplate,
  private val clock: Clock = Clock.systemUTC(),
) {

  /** Inside the purchase's transaction: the goods leave [escrow] for a pickup at [houseStructureId]. */
  fun hold(escrow: ItemContainer, buyerMasterId: Long, sellerMasterId: Long, houseStructureId: Long, townName: String) {
    val waiting = AuctionPickup(
      buyerMasterId = buyerMasterId,
      sellerMasterId = sellerMasterId,
      houseStructureId = houseStructureId,
      townName = townName,
      expiresAt = clock.instant().plus(HELD_FOR),
      container = ItemContainer(ItemContainer.Type.STORAGE),
    )
    inventoryService.moveBetweenEscrows(escrow, waiting.container)
    pickups.save(waiting)
  }

  fun show(accountId: AccountId) {
    val masterId = connections.getMasterId(accountId)
    val rows = transactions.execute {
      val home = homeOfficeOf(masterId)
      pickups.findByBuyerMasterIdOrderByExpiresAt(masterId).map { rowOf(it, home) }
    }.orEmpty()

    out.sendToPlayer(accountId, PickupsSMSG(rows))
  }

  /** In person: the master, or the bestia it drives, stands at the house the goods wait at. */
  fun collect(accountId: AccountId, pickupId: Long) {
    val actorId = connections.getActiveEntityId(accountId)
    val masterId = connections.getMasterId(accountId)
    val (owner, actorAt) = world.read { ItemOwner.of(this, actorId) to get(actorId, Position::class)?.toVec3L() }
    if (owner == null || owner.masterId != masterId || actorAt == null) return refuse(accountId, OpError.REQUEST_REFUSED)

    val arrived = transactions.execute { status ->
      val pickup = pickups.findLockedById(pickupId)?.takeIf { it.buyerMasterId == masterId } ?: return@execute null
      val houseAt = world.read { structures.of(pickup.houseStructureId)?.position }
      if (houseAt == null || houseAt.distance(actorAt) > REACH_TILES) {
        status.setRollbackOnly()
        return@execute emptyList()
      }
      val goods = inventoryService.moveFromEscrow(pickup.container, masterId, owner.playerBestiaId)
      pickups.delete(pickup)
      goods
    }

    when {
      arrived == null -> refuse(accountId, OpError.AUCTION_LISTING_GONE)
      arrived.isEmpty() -> refuse(accountId, OpError.PICKUP_OUT_OF_RANGE)
      else -> world.grantToLive(actorId, arrived)
    }
    show(accountId)
  }

  /** From anywhere: the parcel fee from the house's post office to the buyer's home office, then auction mail. */
  fun post(accountId: AccountId, pickupId: Long) {
    val masterId = connections.getMasterId(accountId)
    val coinId = commodityItems.coinItemId() ?: return refuse(accountId, OpError.REQUEST_REFUSED)

    val fee = transactions.execute { status ->
      val pickup = pickups.findLockedById(pickupId)?.takeIf { it.buyerMasterId == masterId } ?: return@execute null
      val fee = postFeeOf(pickup, homeOfficeOf(masterId))
      if (!inventoryService.consumeAll(masterId, listOf(coinId to fee.toInt()))) {
        status.setRollbackOnly()
        return@execute -1L
      }
      parcels.postInstant(masterId, AuctionService.SENDER_NAME) { inventoryService.moveBetweenEscrows(pickup.container, it) }
      pickups.delete(pickup)
      fee
    }

    when {
      fee == null -> refuse(accountId, OpError.AUCTION_LISTING_GONE)
      fee < 0 -> refuse(accountId, OpError.MAIL_CANNOT_AFFORD)
      else -> {
        world.takeFromLive(connections.getSelectedMasterEntityId(accountId), ReservedItem.stack(coinId, fee.toInt(), weight = 0))
        world.read { reserve.charge(-fee.toDouble(), CoinSource.POSTAGE) }
      }
    }
    show(accountId)
  }

  /** Inside a transaction: unclaimed goods go back to their seller, who keeps the payment. */
  fun returnExpired() {
    for (pickup in pickups.findByExpiresAtBefore(clock.instant())) {
      sendHome(pickup, pickup.sellerMasterId)
    }
  }

  /** Inside a transaction: a closing house sends what waits in it to its buyers. */
  fun sendAllHome(houseStructureId: Long) {
    for (pickup in pickups.findByHouseStructureId(houseStructureId)) {
      sendHome(pickup, pickup.buyerMasterId)
    }
  }

  private fun sendHome(pickup: AuctionPickup, recipientMasterId: Long) {
    parcels.postInstant(recipientMasterId, AuctionService.SENDER_NAME) { inventoryService.moveBetweenEscrows(pickup.container, it) }
    pickups.delete(pickup)
  }

  private fun rowOf(pickup: AuctionPickup, homeOffice: Int?): PickupsSMSG.Row {
    val slot = pickup.container.slots.single()

    return PickupsSMSG.Row(pickup.id, slot.template.id, slot.amount, pickup.townName, pickup.expiresAt, postFeeOf(pickup, homeOffice))
  }

  private fun postFeeOf(pickup: AuctionPickup, homeOffice: Int?): Long {
    val weight = pickup.container.slots.sumOf { it.template.weight * it.amount }
    val origin = world.read { structures.of(pickup.houseStructureId)?.position?.let(offices::playerOfficeAt)?.settlement }
    val km = if (origin != null && homeOffice != null) roads.km(origin, homeOffice) ?: 0.0 else 0.0

    return Freight.fee(km, weight)
  }

  private fun homeOfficeOf(masterId: Long): Int? {
    val master = masters.findByIdOrThrow(masterId)

    return world.read { offices.homeOf(master.homePostOffice, master.homeSettlementName) }
  }

  private fun refuse(accountId: AccountId, code: OpError) {
    out.sendToPlayer(accountId, OperationErrorSMSG(code))
  }

  private companion object {
    /** The docs' 10 days. */
    val HELD_FOR: Duration = Duration.ofDays(10)

    /** From the house's middle; a hall is sixteen metres long, so this is standing at its door. */
    const val REACH_TILES = 12L
  }
}
