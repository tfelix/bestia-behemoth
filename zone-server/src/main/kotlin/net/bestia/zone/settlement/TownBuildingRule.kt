package net.bestia.zone.settlement

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.AreaNameRegistry
import net.bestia.zone.prop.PlacementRule
import net.bestia.zone.prop.PropRestoration
import net.bestia.zone.prop.StructureNeighbourhood
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import kotlin.math.hypot

/**
 * Where a player structure may go up, so the open world is not cluttered with town buildings.
 *
 * A town grows outward in one piece: each new building stands near the town's centre (a player town's sign) or
 * near a building already there. The settlement is looked up where `Place` finds it, so a player's client and
 * this rule agree on where a town ends. Tick thread only, like the registries it reads.
 */
@Component
@Order(1)
class TownBuildingRule(
  private val areaNames: AreaNameRegistry,
  private val neighbourhood: StructureNeighbourhood,
  private val generatedTowns: SettlementSiteIndex,
  private val restoration: PropRestoration,
  private val worldService: WorldService,
) : PlacementRule {

  override fun refusal(kind: StaticEntityKind, ownerMasterId: Long, at: Vec3L): OpError? {
    return refusal(kind, at)
  }

  /** Who builds does not matter here, so a skill can ask before it knows the builder. */
  fun refusal(kind: StaticEntityKind, at: Vec3L): OpError? {
    if (!BuildingPlaces.needsSettlement(kind)) return null

    val settlement = areaNames.at(at.x, at.y) ?: return OpError.STRUCTURE_NEEDS_SETTLEMENT
    val isNearAnchor = isNearCentre(settlement, at) || isNearTownBuilding(at) || isNearGeneratedBuilding(settlement, at)

    return if (isNearAnchor) null else OpError.STRUCTURE_TOO_FAR_FROM_TOWN
  }

  fun isInSettlement(at: Vec3L): Boolean {
    return areaNames.at(at.x, at.y) != null
  }

  private fun isNearCentre(settlement: AreaNameRegistry.Entry, at: Vec3L): Boolean {
    val centre = Vec3L(settlement.x, settlement.y, at.z)

    return neighbourhood.metresBetween(centre, at) <= BuildingPlaces.ANCHOR_RANGE_METRES
  }

  /** A razed generated building anchors nothing. A player town has none: its id is its own, not negative. */
  private fun isNearGeneratedBuilding(settlement: AreaNameRegistry.Entry, at: Vec3L): Boolean {
    if (settlement.id >= 0) return false
    val site = generatedTowns.siteOf(AreaNameRegistry.generatedIndexOf(settlement.id)) ?: return false

    val voxelSize = worldService.config.voxelSize
    val atX = at.x * voxelSize
    val atY = at.y * voxelSize

    return site.buildings.any {
      hypot(it.centre.x - atX, it.centre.y - atY) <= BuildingPlaces.ANCHOR_RANGE_METRES &&
          !restoration.isDestroyed(it.propId)
    }
  }

  /** A construction site anchors nothing until it is finished. */
  private fun isNearTownBuilding(at: Vec3L): Boolean {
    return neighbourhood.within(at, BuildingPlaces.ANCHOR_RANGE_METRES).any {
      !it.isUnderConstruction && BuildingPlaces.anchorsTown(it.kind)
    }
  }
}
