package net.bestia.zone.auction

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.auction.persistence.AuctionClaim
import net.bestia.zone.auction.persistence.AuctionClaimRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.economy.CommodityItems
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.container.ReservedItem
import net.bestia.zone.item.ecs.grantToLive
import net.bestia.zone.item.persistence.ItemRepository
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.AccountId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/** The Collect tab: what the market owes a master, taken in person at any house of the market. */
@Service
class AuctionClaimService(
  private val world: WorldView,
  private val connectionInfoService: ConnectionInfoService,
  private val houses: AuctionHouses,
  private val claims: AuctionClaimRepository,
  private val inventoryService: InventoryService,
  private val itemRepository: ItemRepository,
  private val commodityItems: CommodityItems,
  private val outMessageProcessor: OutMessageProcessor,
  private val transactions: TransactionTemplate,
) {

  fun show(accountId: AccountId, houseEntityId: Long) {
    if (!inReach(accountId, houseEntityId)) {
      return outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(OpError.AUCTION_OUT_OF_RANGE))
    }

    send(accountId, connectionInfoService.getMasterId(accountId))
  }

  /** Collects one claim, or every claim for [claimId] 0. */
  fun collect(accountId: AccountId, houseEntityId: Long, claimId: Long) {
    if (!inReach(accountId, houseEntityId)) {
      return outMessageProcessor.sendToPlayer(accountId, OperationErrorSMSG(OpError.AUCTION_OUT_OF_RANGE))
    }

    val masterId = connectionInfoService.getMasterId(accountId)
    val coinId = commodityItems.coinItemId() ?: return

    val collected = transactions.execute { take(masterId, claimId, coinId) }.orEmpty()
    world.grantToLive(connectionInfoService.getSelectedMasterEntityId(accountId), collected)

    send(accountId, masterId)
  }

  private fun take(masterId: Long, claimId: Long, coinId: Long): List<ReservedItem> {
    val owed = claims.findByMasterIdOrderByCreatedAt(masterId).filter { claimId == 0L || it.id == claimId }

    val collected = ArrayList<ReservedItem>()
    var coins = 0L

    for (claim in owed) {
      // A purse holds an Int of coins; what does not fit waits for the next collect.
      if (coins + claim.coins > Int.MAX_VALUE) break

      collected += inventoryService.moveFromEscrow(claim.container, masterId)
      coins += claim.coins
      claims.delete(claim)
    }

    if (coins > 0) {
      inventoryService.grantToMaster(masterId, coinId, coins.toInt())
      val weight = itemRepository.findByIdOrNull(coinId)?.weight ?: 0
      collected += ReservedItem.stack(coinId, coins.toInt(), weight)
    }

    return collected
  }

  private fun send(accountId: AccountId, masterId: Long) {
    val rows = claims.findByMasterIdOrderByCreatedAt(masterId).map(::rowOf)
    outMessageProcessor.sendToPlayer(accountId, AuctionClaimsSMSG(rows))
  }

  private fun rowOf(claim: AuctionClaim): AuctionClaimsSMSG.Row {
    return AuctionClaimsSMSG.Row(claim.id, claim.reason, claim.coins, claim.itemId, claim.amount)
  }

  private fun inReach(accountId: AccountId, houseEntityId: Long): Boolean {
    val actorId = connectionInfoService.getActiveEntityId(accountId)

    return world.read { houses.townInReach(this, actorId, houseEntityId) } != null
  }
}
