package net.bestia.zone.postal.office

import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.zone.cartography.render.PlaceNames
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSiteIndex
import net.bestia.zone.world.settlement.StartTownMarkets
import org.springframework.stereotype.Service

/**
 * The post offices the world generator placed: one in every village and town, kept by a postmaster. See the
 * postal page of the design docs. Tick-thread only, like [SettlementSiteIndex].
 */
@Service
class PostOffices(
  private val worldService: WorldService,
  private val siteIndex: SettlementSiteIndex,
  private val startTowns: StartTownMarkets,
) {

  /** [level] 1 to 3 sets how often the coach leaves; see the design docs' office levels. */
  data class Office(val settlement: Int, val name: String, val level: Int)

  private val namesBySettlement: Map<Int, String> by lazy {
    val chronicle = worldService.generated.world.chronicle
    worldService.generated.world.features.all()
      .asSequence()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
      .mapNotNull { marker ->
        val name = PlaceNames.nameOf(chronicle, marker) ?: return@mapNotNull null
        marker.attribute(SettlementChannels.INDEX).toInt() to name
      }
      .toMap()
  }

  /** The office of [settlement], or null for a hamlet or a settlement that is not standing. */
  fun officeOf(settlement: Int): Office? {
    val site = siteIndex.siteOf(settlement) ?: return null
    if (site.buildingsFor(POST_OFFICE_TYPE).isEmpty()) return null

    val name = namesBySettlement[settlement] ?: return null
    val level = if (startTowns.isStartTown(settlement)) START_TOWN_LEVEL else GENERATED_LEVEL

    return Office(settlement, name, level)
  }

  /** Where the coach stops: the doorstep of the office's building. */
  fun doorOf(settlement: Int): Vec3L? {
    val building = siteIndex.siteOf(settlement)?.buildingsFor(POST_OFFICE_TYPE)?.firstOrNull() ?: return null
    return siteIndex.doorstepOf(building)
  }

  /** Straight-line kilometres between two offices' settlements, for the docs' "within 10 km of home". */
  fun kmBetween(one: Int, other: Int): Double? {
    val a = siteIndex.siteOf(one)?.centre ?: return null
    val b = siteIndex.siteOf(other)?.centre ?: return null
    return a.distanceTo(b) / 1000.0
  }

  /** The settlement a master's parcels go to: the one picked, else the one the master started in. */
  fun homeOf(homePostOffice: Int?, homeSettlementName: String): Int? {
    return homePostOffice ?: namesBySettlement.entries.firstOrNull { it.value == homeSettlementName }?.key
  }

  companion object {
    const val POST_OFFICE = "post_office"

    private val POST_OFFICE_TYPE = BusinessCatalogue.ALL.indexOfFirst { it.id == POST_OFFICE }

    private const val GENERATED_LEVEL = 2
    private const val START_TOWN_LEVEL = 3
  }
}
