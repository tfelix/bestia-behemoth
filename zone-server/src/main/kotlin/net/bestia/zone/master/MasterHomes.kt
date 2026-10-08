package net.bestia.zone.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.account.persistence.MasterRepository
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.world.SpawnPointAvailability
import net.bestia.zone.world.settlement.SettlementFates
import org.springframework.stereotype.Service

/**
 * Gives the masters of a fallen town the first home still on offer. Only their home moves: a master out in
 * the world stays where they stand, and gets up at the new home the next time they die.
 */
@Service
class MasterHomes(
  private val masterRepository: MasterRepository,
  private val availability: SpawnPointAvailability,
  private val jobs: AsyncJobExecutor,
  fates: SettlementFates,
) {

  init {
    fates.onFell(::rehome)
  }

  private fun rehome(settlement: Int) {
    val fallen = availability.homeNameOf(settlement) ?: return

    // The watch decides again only every thirty seconds, and until then it still offers the fallen town.
    availability.refresh()
    val home = availability.offered().firstOrNull()
    if (home == null) {
      LOG.error { "Town '$fallen' fell and no home is left to offer, so its masters keep it" }
      return
    }

    val (x, y, z) = home.position
    jobs.submit(JOB_KEY) {
      val moved = masterRepository.rehome(fallen, home.settlementName, x, y, z)
      LOG.info { "Town '$fallen' fell; $moved masters now live in '${home.settlementName}'" }
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** One key, so a second fall cannot overtake the first and move masters to a home that just fell. */
    const val JOB_KEY = "master-homes"
  }
}
