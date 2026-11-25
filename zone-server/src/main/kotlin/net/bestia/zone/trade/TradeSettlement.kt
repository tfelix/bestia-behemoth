package net.bestia.zone.trade

import net.bestia.zone.account.AvailableBestiaSlotService
import net.bestia.zone.account.persistence.Master
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.account.persistence.StoredVitals
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.item.bundle.ItemBundles
import net.bestia.zone.item.container.InventoryService
import net.bestia.zone.item.persistence.ItemInstance
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * The exchange of one trade as a single transaction: the items ([InventoryService.settleTrade]) and the bestias
 * change hands together, or nothing does.
 */
@Service
class TradeSettlement(
  private val inventoryService: InventoryService,
  private val playerBestias: PlayerBestiaRepository,
  private val masters: MasterRepository,
  private val slots: AvailableBestiaSlotService,
  private val bundles: ItemBundles,
) {

  /** A party bestia that left its old owner's world, to change hands. */
  class BestiaMove(
    val playerBestiaId: PlayerBestiaId,
    val fromMasterId: Long,
    val toMasterId: Long,
    val vitals: StoredVitals,
  )

  /** Where a bestia ended up with its new owner. */
  class Arrival(
    val move: BestiaMove,
    /** Into storage, as the new owner's party was full. */
    val stored: Boolean,
    /** What it carried, packed because a stored bestia carries nothing. Null when it carried nothing or is out. */
    val bundle: ItemInstance?,
  )

  class Result(
    val items: InventoryService.Settlement,
    val arrivals: List<Arrival>,
  )

  /** The receiving master has room neither in the party nor in storage. The whole trade rolls back. */
  class NoRoomException(val masterName: String) : RuntimeException("$masterName has no room for another bestia")

  /** A bestia is no longer the party bestia it was offered as. The whole trade rolls back. */
  class BestiaGoneException(message: String) : RuntimeException(message)

  /** @throws NoRoomException when a bestia finds no free slot with its new owner */
  @Transactional
  fun settle(
    tradeId: Long,
    masterAId: Long,
    masterBId: Long,
    expectedA: Set<Long>,
    expectedB: Set<Long>,
    bestias: List<BestiaMove>,
    now: Instant,
  ): Result {
    // First, because it locks both master rows in id order; the bestia rows come after, in one order too.
    val items = inventoryService.settleTrade(tradeId, masterAId, masterBId, expectedA, expectedB)

    val moves = bestias.sortedBy { it.playerBestiaId }
    for (move in moves) {
      val row = playerBestias.findByIdForUpdate(move.playerBestiaId)
        ?.takeIf { it.master.id == move.fromMasterId && !it.isStored }
        ?: throw BestiaGoneException("Bestia ${move.playerBestiaId} is no longer a party bestia of ${move.fromMasterId}")
      row.changeOwner(masters.findByIdOrThrow(move.toMasterId))
    }

    // Every bestia has changed sides before any is placed, so a master giving one away makes room for theirs.
    val arrivals = moves.groupBy { it.toMasterId }.flatMap { (toMasterId, incoming) ->
      place(masters.findByIdOrThrow(toMasterId), incoming, now)
    }

    return Result(items, arrivals)
  }

  /**
   * The first of [incoming] that fit stay in the party; the rest go into storage, packing what they carry.
   */
  private fun place(receiver: Master, incoming: List<BestiaMove>, now: Instant): List<Arrival> {
    val accountId = receiver.account.id
    val activeSlots = slots.getTotalSlotCount(accountId)
    val storageSlots = slots.getStorageSlotCount(accountId)
    val stored = HashMap<PlayerBestiaId, ItemInstance?>()

    for (move in incoming.reversed()) {
      if (receiver.bestias.activeBestias.size <= activeSlots) {
        break
      }
      if (receiver.bestias.storedBestias.size >= storageSlots) {
        throw NoRoomException(receiver.name)
      }

      val row = playerBestias.findByIdForUpdate(move.playerBestiaId)!!
      row.store(now, restsUntil = now.plus(PlayerBestia.STORAGE_REST), move.vitals)
      stored[move.playerBestiaId] = bundles.packAll(row.container)
    }

    return incoming.map { move -> Arrival(move, move.playerBestiaId in stored, stored[move.playerBestiaId]) }
  }
}
