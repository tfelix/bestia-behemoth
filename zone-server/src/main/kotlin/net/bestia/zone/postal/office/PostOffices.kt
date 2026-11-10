package net.bestia.zone.postal.office

import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.pop.BusinessCatalogue
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.cartography.render.PlaceNames
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.settlement.Town
import net.bestia.zone.settlement.TownRegistry
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSiteIndex
import net.bestia.zone.world.settlement.StartTownMarkets
import org.springframework.stereotype.Service

/**
 * The post offices: the ones the world generator placed, one in every village and town kept by a postmaster, and
 * the ones players built in their towns. See the postal page of the design docs. Tick-thread only, like
 * [SettlementSiteIndex].
 *
 * Both are named by an int, as parcels and masters store them: a settlement index for a generated office, and
 * [PLAYER_OFFICE_BASE] plus the town id for a player one.
 */
@Service
class PostOffices(
  private val worldService: WorldService,
  private val siteIndex: SettlementSiteIndex,
  private val startTowns: StartTownMarkets,
  private val towns: TownRegistry,
  private val structures: PlayerStructureRegistry,
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
    if (settlement >= PLAYER_OFFICE_BASE) return playerOfficeOf(settlement)

    val site = siteIndex.siteOf(settlement) ?: return null
    if (site.buildingsFor(POST_OFFICE_TYPE).isEmpty()) return null

    val name = namesBySettlement[settlement] ?: return null
    val level = if (startTowns.isStartTown(settlement)) START_TOWN_LEVEL else GENERATED_LEVEL

    return Office(settlement, name, level)
  }

  /** Where the coach stops: the doorstep of the office's building. */
  fun doorOf(settlement: Int): Vec3L? {
    if (settlement >= PLAYER_OFFICE_BASE) return playerOfficeOf(settlement)?.let { buildingOf(townOf(settlement)!!) }

    val building = siteIndex.siteOf(settlement)?.buildingsFor(POST_OFFICE_TYPE)?.firstOrNull() ?: return null
    return siteIndex.doorstepOf(building)
  }

  /** Straight-line kilometres between two offices' settlements, for the docs' "within 10 km of home". */
  fun kmBetween(one: Int, other: Int): Double? {
    val a = centreMetresOf(one) ?: return null
    val b = centreMetresOf(other) ?: return null
    return a.distanceTo(b) / 1000.0
  }

  /** The office of the standing town around [position], if that town has finished one. */
  fun playerOfficeAt(position: Vec3L): Office? {
    val town = towns.standingAt(position) ?: return null

    return playerOfficeOf(PLAYER_OFFICE_BASE + town.id.toInt())
  }

  /** The open office nearest to [settlement]'s, other than it: where its mail and residents go when it closes. */
  fun successorOf(settlement: Int): Int? {
    val at = centreMetresOf(settlement) ?: return null
    val generated = namesBySettlement.keys.filter { officeOf(it) != null }
    val player = towns.all().map { PLAYER_OFFICE_BASE + it.id.toInt() }.filter { officeOf(it) != null }

    return (generated + player)
      .filter { it != settlement }
      .minByOrNull { centreMetresOf(it)?.distanceTo(at) ?: Double.MAX_VALUE }
  }

  /** Where [settlement]'s office stands, in metres. */
  fun centreMetresOf(settlement: Int): Vec2d? {
    if (settlement < PLAYER_OFFICE_BASE) return siteIndex.siteOf(settlement)?.centre

    val voxelSize = worldService.config.voxelSize
    val sign = townOf(settlement)?.sign ?: return null

    return Vec2d(sign.x * voxelSize, sign.y * voxelSize)
  }

  /** The post runs a player town's office: it has no postmaster hours and starts at the lowest level. */
  private fun playerOfficeOf(settlement: Int): Office? {
    val town = townOf(settlement)?.takeIf { it.isStanding } ?: return null
    buildingOf(town) ?: return null

    return Office(settlement, town.name, PLAYER_LEVEL)
  }

  private fun townOf(settlement: Int): Town? {
    return towns.of((settlement - PLAYER_OFFICE_BASE).toLong())
  }

  private fun buildingOf(town: Town): Vec3L? {
    return structures.all()
      .firstOrNull {
        it.kind == StaticEntityKind.POST_OFFICE && !it.isUnderConstruction &&
          towns.metresBetween(town.sign, it.position) <= town.size.radiusMetres
      }
      ?.position
  }

  /** The settlement a master's parcels go to: the one picked, else the one the master started in. */
  fun homeOf(homePostOffice: Int?, homeSettlementName: String): Int? {
    return homePostOffice ?: namesBySettlement.entries.firstOrNull { it.value == homeSettlementName }?.key
  }

  companion object {
    const val POST_OFFICE = "post_office"

    private val POST_OFFICE_TYPE = BusinessCatalogue.ALL.indexOfFirst { it.id == POST_OFFICE }

    /** Far above every settlement index the generator hands out. */
    const val PLAYER_OFFICE_BASE = 1_000_000

    private const val PLAYER_LEVEL = 1
    private const val GENERATED_LEVEL = 2
    private const val START_TOWN_LEVEL = 3
  }
}
