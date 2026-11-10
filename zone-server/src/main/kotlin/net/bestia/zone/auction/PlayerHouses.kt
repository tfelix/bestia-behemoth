package net.bestia.zone.auction

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.auction.persistence.PlayerAuctionHouse
import net.bestia.zone.auction.persistence.PlayerAuctionHouseRepository
import net.bestia.zone.persistence.AsyncJobExecutor
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Who owns each player auction house and what fee it charges, in memory for the tick's reach checks and the
 * market's searches alike. The till is not mirrored: it changes in the market's transactions only.
 */
@Service
class PlayerHouses(
  private val repository: PlayerAuctionHouseRepository,
  private val masters: MasterRepository,
  private val jobs: AsyncJobExecutor,
  private val links: TradeLinks,
) {

  data class House(
    val structureId: Long,
    val ownerMasterId: Long,
    val ownerAccountId: Long,
    val ownerName: String,
    val feePercent: Int,
    val feeChangedAt: Instant?,
  )

  private val byStructure = ConcurrentHashMap<Long, House>()

  fun of(structureId: Long): House? {
    return byStructure[structureId]
  }

  fun ownedBy(masterId: Long): List<House> {
    return byStructure.values.filter { it.ownerMasterId == masterId }
  }

  /** The houses whose listings [house] shows: its owner's, and those of every owner its owner has an agreement with. */
  fun sharingWith(house: House): Set<Long> {
    val owners = links.partnersOf(house.ownerMasterId).map { it.masterId } + house.ownerMasterId

    return owners.flatMap(::ownedBy).mapTo(HashSet()) { it.structureId }
  }

  /**
   * On the tick, when the site is placed: the house counts towards its owner's limit at once, and its row and
   * its owner's name follow in a job, since the tick may not query the database.
   */
  fun register(structureId: Long, ownerMasterId: Long, ownerAccountId: Long, feePercent: Int) {
    byStructure[structureId] = House(structureId, ownerMasterId, ownerAccountId, "", feePercent, feeChangedAt = null)

    jobs.submit(structureId) {
      val ownerName = masters.findByIdOrThrow(ownerMasterId).name
      remember(repository.save(PlayerAuctionHouse(structureId, ownerMasterId, ownerAccountId, ownerName, feePercent, null)))
    }
  }

  /** Called inside the transaction that wrote the row, so memory follows the database. */
  fun remember(row: PlayerAuctionHouse) {
    byStructure[row.structureId] = houseOf(row)
  }

  fun forget(structureId: Long) {
    byStructure.remove(structureId)
  }

  /** Boot only. */
  fun loadAll() {
    repository.findAll().forEach(::remember)
  }

  private fun houseOf(row: PlayerAuctionHouse): House {
    return House(row.structureId, row.ownerMasterId, row.ownerAccountId, row.ownerName, row.feePercent, row.feeChangedAt)
  }
}
