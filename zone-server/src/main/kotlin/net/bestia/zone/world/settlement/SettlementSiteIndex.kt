package net.bestia.zone.world.settlement

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.civ.BuildingChannels
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.pop.BusinessChannels
import net.bestia.worldgen.pop.EconomyProbe
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.voxel.BuildingProps
import net.bestia.zone.ecs.spawn.ambient.StandingSettlements
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import org.springframework.stereotype.Service

/**
 * A settlement's buildings, doors and trades, built the first time somebody asks about that settlement.
 *
 * Lazy per settlement rather than indexed at boot, which is `TownClearance`'s trade and the same argument:
 * a world holds hundreds of settlements and a player is in one of them. Boot-time indexing would pay for
 * every town to serve the one they walked into, and the feature store offers a bucketed
 * [net.bestia.worldgen.core.FeatureStore.query] that makes the per-settlement cost small.
 *
 * Tick-thread only, like the registries it sits beside: the maps are plain and unsynchronised.
 */
@Service
class SettlementSiteIndex(
  private val worldService: WorldService
) {

  /**
   * Its own copy of the settlement list, which the ambient layer also builds.
   *
   * Two scans of the feature store per process rather than one. Worth folding into a shared bean if a
   * third consumer appears; not worth reaching into `AmbientSiteResolver`'s private state for two.
   */
  private val settlements: StandingSettlements by lazy {
    StandingSettlements.of(worldService.generated)
  }

  private val sites = HashMap<Int, SettlementSite>()

  /** The settlement [index], or null when history left nothing standing there. */
  fun siteOf(index: Int): SettlementSite? {
    sites[index]?.let { return it }

    val entry = settlements.entryOf(index) ?: return null
    val site = build(entry)
    sites[index] = site

    return site
  }

  /**
   * The settlement containing ([x], [y]), given in **position units**, or null out in the country.
   *
   * Smallest footprint wins, so a hamlet inside a city's graded ground answers for itself.
   */
  fun siteCovering(x: Long, y: Long): SettlementSite? {
    val metres = worldService.config.voxelSize
    val covering = settlements.coveringWithin(x * metres, y * metres, 0.0)

    return covering
      .minByOrNull { it.footprintRadius }
      ?.let { siteOf(it.index) }
  }

  /**
   * The settlements whose graded footprint comes within [reach] of ([x], [y]), all in **position units**.
   *
   * Indices rather than sites, and deliberately: this is the coarse half of a two-phase test, so it must
   * not build a site for every town it merely rules in. The caller asks [siteOf] for the ones it keeps.
   */
  fun coveringWithin(x: Long, y: Long, reach: Long): List<Int> {
    val metres = worldService.config.voxelSize

    return settlements
      .coveringWithin(x * metres, y * metres, reach * metres)
      .map { it.index }
  }

  /**
   * A building's doorstep as somewhere to stand.
   *
   * Feature positions are metres and an entity's are position units. The height is the building's own
   * graded floor rather than a fresh surface sample: the door is against the wall, and the two would
   * disagree by whatever cut or fill the town stage applied there.
   */
  fun doorstepOf(building: SettlementSite.Building): Vec3L {
    val config = worldService.config

    return Vec3L(
      Math.round(building.door.x / config.voxelSize),
      Math.round(building.door.y / config.voxelSize),
      ChunkCoords.standingZ(config, building.floorElevation)
    )
  }

  /**
   * Somewhere in the open as a place to stand, converting metres to position units.
   *
   * Height comes from the terrain rather than from a building's graded floor, which is the difference
   * from [doorstepOf]: out in a square there is no floor to stand on but the ground itself.
   */
  fun standingAt(point: Vec2d): Vec3L {
    val config = worldService.config
    val x = Math.round(point.x / config.voxelSize)
    val y = Math.round(point.y / config.voxelSize)

    return Vec3L(x, y, ChunkCoords.standingZ(config, worldService.generated.base.heightAt(point.x, point.y)))
  }

  private fun build(entry: StandingSettlements.Entry): SettlementSite {
    val area = Aabb(
      entry.x - entry.footprintRadius,
      entry.y - entry.footprintRadius,
      entry.x + entry.footprintRadius,
      entry.y + entry.footprintRadius
    )
    val features = worldService.generated.world.features.query(area)

    // A business sits on its host building's centre and carries no reference to it, so the join is the
    // building-naming lattice both sides already quantise to.
    val tradeByPropId = HashMap<Long, Int>()
    for (feature in features) {
      if (feature.kind != FeatureKind.BUSINESS || feature !is PointMarker) continue
      runCatching {
        if (feature.attribute(BusinessChannels.SETTLEMENT).toInt() != entry.index) return@runCatching
        val propId = BuildingProps.propIdAt(feature.position.x, feature.position.y)
        tradeByPropId[propId] = feature.attribute(BusinessChannels.TYPE).toInt()
      }
    }

    val buildings = ArrayList<SettlementSite.Building>()
    for (feature in features) {
      if (feature.kind != FeatureKind.BUILDING || feature !is FootprintFeature) continue
      // `BuildingProps` swallows a footprint with missing channels for the same reason: a hand-built one in
      // a test should cost that building, not the whole settlement.
      runCatching { buildingOf(feature, entry.index, tradeByPropId) }
        .getOrNull()
        ?.let { buildings.add(it) }
    }

    LOG.debug { "Settlement ${entry.index}: ${buildings.size} building(s), ${tradeByPropId.size} trade(s)" }

    return SettlementSite(
      index = entry.index,
      centre = Vec2d(entry.x, entry.y),
      tier = entry.tier,
      population = EconomyProbe.summaryFor(worldService.generated.world, entry.index, area),
      unordered = buildings
    )
  }

  private fun buildingOf(
    footprint: FootprintFeature,
    settlement: Int,
    tradeByPropId: Map<Long, Int>
  ): SettlementSite.Building? {
    if (footprint.attribute(BuildingChannels.SETTLEMENT).toInt() != settlement) return null

    val door = DoorPoint.of(
      footprint,
      Vec2d(
        footprint.attribute(BuildingChannels.DOOR_X),
        footprint.attribute(BuildingChannels.DOOR_Y)
      )
    ) ?: return null

    val propId = BuildingProps.propIdAt(footprint.center.x, footprint.center.y)

    return SettlementSite.Building(
      propId = propId,
      function = BuildingFunction.entries[footprint.attribute(BuildingChannels.FUNCTION).toInt()],
      centre = footprint.center,
      door = door,
      floorElevation = footprint.attribute(BuildingChannels.FLOOR_ELEVATION),
      businessType = tradeByPropId[propId] ?: NO_BUSINESS
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    const val NO_BUSINESS = -1
  }
}
