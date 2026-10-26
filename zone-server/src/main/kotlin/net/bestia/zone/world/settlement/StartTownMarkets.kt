package net.bestia.zone.world.settlement

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.zone.world.MasterSpawnPointService
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/**
 * The market halls of the start towns, which are the only auction houses the world provides. Every other town
 * waits for a player to build one.
 *
 * The towns are the first homes [MasterSpawnPointService] ever offered, not the ones it offers now: a fallen
 * town keeps its hall, so the set of auction houses never moves under a listing.
 *
 * Tick-thread only, like [SettlementSiteIndex].
 */
@Service
class StartTownMarkets(
  private val worldService: WorldService,
  private val siteIndex: SettlementSiteIndex
) {

  private val hallPropIds: Set<Long> by lazy { findHalls() }

  fun isAuctionHouse(propId: Long): Boolean {
    return propId in hallPropIds
  }

  private fun findHalls(): Set<Long> {
    // The same choice MasterSpawnPointService persists, so its first ranks are these towns.
    val towns = SettlementSpawnPoints.choose(worldService.generated, MasterSpawnPointService.HOMES_HELD)
      .take(MasterSpawnPointService.HOMES_OFFERED)

    val halls = HashSet<Long>()

    for (town in towns) {
      val markets = siteIndex.siteOf(town.settlementIndex)?.buildingsOf(BuildingFunction.MARKET).orEmpty()

      if (markets.isEmpty()) {
        LOG.warn { "Start town ${town.name} has no market hall, so it has no auction house" }
      }

      markets.mapTo(halls) { it.propId }
    }

    LOG.info { "Auction houses stand in: ${towns.joinToString { it.name }}" }

    return halls
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
