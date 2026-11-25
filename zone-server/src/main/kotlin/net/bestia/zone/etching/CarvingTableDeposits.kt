package net.bestia.zone.etching

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.crafting.CarvingTableContents
import net.bestia.zone.crafting.CarvingTableContents.Contents
import net.bestia.zone.crafting.CarvingTableContents.Deposited
import net.bestia.zone.crafting.CarvingTableContents.Depositable
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class CarvingTableDeposits(
  private val masters: MasterRepository,
  private val playerBestias: PlayerBestiaRepository,
  private val transactions: TransactionTemplate,
) : CarvingTableContents {

  override fun load(structureId: Long, viewerMasterId: Long): Contents {
    return transactions.execute {
      val deposited = playerBestias.findAllByDepositedAtStructureId(structureId).sortedBy { it.id }
        .map { deposited(it, viewerMasterId) }
      val own = masters.findByIdOrThrow(viewerMasterId).bestias
      val depositable = (own.activeBestias + own.storedBestias).sortedBy { it.id }.map(::depositable)

      Contents(deposited, depositable)
    }!!
  }

  private fun deposited(bestia: PlayerBestia, viewerMasterId: Long): Deposited {
    return Deposited(
      playerBestiaId = bestia.id,
      speciesId = bestia.bestia.id,
      level = bestia.level,
      name = bestia.name,
      ownerName = bestia.master.name,
      mine = bestia.master.id == viewerMasterId,
    )
  }

  private fun depositable(bestia: PlayerBestia): Depositable {
    return Depositable(bestia.id, bestia.bestia.id, bestia.level, bestia.name, inStorage = bestia.isStored)
  }
}
