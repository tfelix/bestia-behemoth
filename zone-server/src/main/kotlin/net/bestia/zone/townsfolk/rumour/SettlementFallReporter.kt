package net.bestia.zone.townsfolk.rumour

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementFates
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Service

/**
 * Tells the towns around a settlement that it fell.
 *
 * At once and from the town itself for now. Once survivors walk to the next town, the news should travel with
 * them instead, and arrive only where they do.
 */
@Service
class SettlementFallReporter(
  private val rumours: RumourService,
  private val sites: SettlementSiteIndex,
  private val worldService: WorldService,
  fates: SettlementFates,
) {

  init {
    fates.onFell(::report)
  }

  private fun report(settlement: Int) {
    val site = sites.siteOf(settlement) ?: return
    val voxelSize = worldService.config.voxelSize

    val heard = rumours.post(
      kind = RumourKind.TOWN_FELL,
      voxelX = (site.centre.x / voxelSize).toLong(),
      voxelY = (site.centre.y / voxelSize).toLong(),
      strength = STRENGTH,
    )

    LOG.info { "The fall of settlement $settlement reached ${heard.size} settlement(s)" }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** As far as any news travels: a town gone is the largest thing that happens nearby. */
    const val STRENGTH = 1.0
  }
}
