package net.bestia.zone.etching

import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.account.persistence.findByIdOrThrow
import net.bestia.zone.crafting.CarvingTableContents
import net.bestia.zone.crafting.CarvingTableContents.Contents
import net.bestia.zone.crafting.CarvingTableContents.Deposited
import net.bestia.zone.crafting.CarvingTableContents.Depositable
import net.bestia.zone.etching.persistence.EtchingJob
import net.bestia.zone.etching.persistence.EtchingJobRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import kotlin.math.roundToInt

@Component
class CarvingTableDeposits(
  private val masters: MasterRepository,
  private val playerBestias: PlayerBestiaRepository,
  private val jobs: EtchingJobRepository,
  private val transactions: TransactionTemplate,
  private val clock: Clock = Clock.systemUTC(),
) : CarvingTableContents {

  override fun load(structureId: Long, viewerMasterId: Long, etchSecondsPerLevelSquared: Float): Contents {
    return transactions.execute {
      val running = jobs.findAllByStructureId(structureId).associateBy { it.playerBestiaId }
      val deposited = playerBestias.findAllByDepositedAtStructureId(structureId).sortedBy { it.id }
        .map { deposited(it, viewerMasterId, etchSecondsPerLevelSquared, running[it.id]) }
      val own = masters.findByIdOrThrow(viewerMasterId).bestias
      val depositable = (own.activeBestias + own.storedBestias).sortedBy { it.id }.map(::depositable)

      Contents(deposited, depositable)
    }!!
  }

  private fun deposited(bestia: PlayerBestia, viewerMasterId: Long, secondsPerLevelSquared: Float, job: EtchingJob?): Deposited {
    val secondsLeft = job?.let { Duration.between(clock.instant(), it.endsAt).seconds.coerceAtLeast(1).toInt() } ?: 0

    return Deposited(
      playerBestiaId = bestia.id,
      speciesId = bestia.bestia.id,
      level = bestia.level,
      name = bestia.name,
      ownerName = bestia.master.name,
      mine = bestia.master.id == viewerMasterId,
      etchSeconds = (secondsPerLevelSquared * bestia.level * bestia.level).roundToInt(),
      etchSecondsLeft = secondsLeft,
      etcherName = job?.artificerName,
      etchingMine = job?.artificerMasterId == viewerMasterId,
    )
  }

  private fun depositable(bestia: PlayerBestia): Depositable {
    return Depositable(bestia.id, bestia.bestia.id, bestia.level, bestia.name, inStorage = bestia.isStored)
  }
}
