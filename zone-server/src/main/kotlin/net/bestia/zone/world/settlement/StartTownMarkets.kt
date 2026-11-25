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

  /** One market hall and the start town it stands in. */
  class Hall(val townName: String, val building: SettlementSite.Building)

  private val halls: List<Hall> by lazy { findHalls() }

  /** Hall prop id to the name of its town. */
  private val townByHall: Map<Long, String> by lazy { halls.associate { it.building.propId to it.townName } }

  private val startTownIndexes: Set<Int> by lazy { startTowns().mapTo(HashSet()) { it.settlementIndex } }

  fun isAuctionHouse(propId: Long): Boolean {
    return propId in townByHall
  }

  fun townOf(propId: Long): String? {
    return townByHall[propId]
  }

  fun isStartTown(settlementIndex: Int): Boolean {
    return settlementIndex in startTownIndexes
  }

  /** The towns with a hall. They share one market. */
  fun towns(): List<String> {
    return townByHall.values.distinct()
  }

  fun halls(): List<Hall> {
    return halls
  }

  private fun findHalls(): List<Hall> {
    val towns = startTowns()
    val halls = ArrayList<Hall>()

    for (town in towns) {
      val markets = siteIndex.siteOf(town.settlementIndex)?.buildingsOf(BuildingFunction.MARKET).orEmpty()

      if (markets.isEmpty()) {
        LOG.warn { "Start town ${town.name} has no market hall, so it has no auction house" }
      }

      markets.forEach { halls.add(Hall(town.name, it)) }
    }

    LOG.info { "Auction houses stand in: ${towns.joinToString { it.name }}" }

    return halls
  }

  // The same choice MasterSpawnPointService persists, so its first ranks are these towns.
  private fun startTowns(): List<SettlementSpawnPoints.Candidate> {
    return SettlementSpawnPoints.choose(worldService.generated, MasterSpawnPointService.HOMES_HELD)
      .take(MasterSpawnPointService.HOMES_OFFERED)
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
