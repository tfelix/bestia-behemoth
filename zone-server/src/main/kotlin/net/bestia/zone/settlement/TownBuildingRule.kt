package net.bestia.zone.settlement

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.prop.PlacementRule
import net.bestia.zone.prop.PropRestoration
import net.bestia.zone.prop.StructureNeighbourhood
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.settlement.SettlementSite
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Component
import kotlin.math.hypot

/**
 * Where a player structure may go up, so the open world is not cluttered with town buildings.
 *
 * A town grows outward in one piece: each new building stands near the sign or near a building already there.
 * Tick thread only, like the registries it reads.
 */
@Component
class TownBuildingRule(
  private val towns: TownRegistry,
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

    val playerTown = towns.standingAt(at)
    val generatedTown = generatedTowns.siteCovering(at.x, at.y)
    val isNearAnchor = when {
      playerTown != null -> isNearSign(playerTown, at) || isNearTownBuilding(at)
      generatedTown != null -> isNearGeneratedBuilding(generatedTown, at) || isNearTownBuilding(at)
      else -> return OpError.STRUCTURE_NEEDS_SETTLEMENT
    }

    return if (isNearAnchor) null else OpError.STRUCTURE_TOO_FAR_FROM_TOWN
  }

  fun isInSettlement(at: Vec3L): Boolean {
    return towns.standingAt(at) != null || generatedTowns.siteCovering(at.x, at.y) != null
  }

  private fun isNearSign(town: Town, at: Vec3L): Boolean {
    return neighbourhood.metresBetween(town.sign, at) <= BuildingPlaces.ANCHOR_RANGE_METRES
  }

  /** A razed generated building anchors nothing. */
  private fun isNearGeneratedBuilding(town: SettlementSite, at: Vec3L): Boolean {
    val voxelSize = worldService.config.voxelSize
    val atX = at.x * voxelSize
    val atY = at.y * voxelSize

    return town.buildings.any {
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
