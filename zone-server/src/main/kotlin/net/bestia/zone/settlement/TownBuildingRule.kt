package net.bestia.zone.settlement

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.prop.PlacementRule
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.StructureEntry
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import kotlin.math.ceil
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
  private val structures: PlayerStructureRegistry,
  private val worldService: WorldService,
) : PlacementRule {

  override fun refusal(kind: StaticEntityKind, ownerMasterId: Long, at: Vec3L): OpError? {
    return refusal(kind, at)
  }

  /** Who builds does not matter here, so a skill can ask before it knows the builder. */
  fun refusal(kind: StaticEntityKind, at: Vec3L): OpError? {
    if (!BuildingPlaces.needsSettlement(kind)) return null

    val town = towns.standingAt(at) ?: return OpError.STRUCTURE_NEEDS_SETTLEMENT

    return if (isNearSign(town, at) || isNearTownBuilding(at)) null else OpError.STRUCTURE_TOO_FAR_FROM_TOWN
  }

  private fun isNearSign(town: Town, at: Vec3L): Boolean {
    return metresBetween(town.sign, at) <= BuildingPlaces.ANCHOR_RANGE_METRES
  }

  /** Only a finished building that adds to a town anchors the next one, so a chest or a site cannot. */
  private fun isNearTownBuilding(at: Vec3L): Boolean {
    return structuresAround(at, BuildingPlaces.ANCHOR_RANGE_METRES).any {
      !it.isUnderConstruction &&
          TownGrowth.pointsOf(it.kind) > 0.0 &&
          metresBetween(it.position, at) <= BuildingPlaces.ANCHOR_RANGE_METRES
    }
  }

  private fun structuresAround(at: Vec3L, rangeMetres: Double): List<StructureEntry> {
    val config = worldService.config
    val chunkSize = config.chunkSize.toLong()
    val rangeUnits = ceil(rangeMetres / config.voxelSize).toLong()
    val firstChunkX = Math.floorDiv(at.x - rangeUnits, chunkSize).toInt()
    val lastChunkX = Math.floorDiv(at.x + rangeUnits, chunkSize).toInt()
    val firstChunkY = Math.floorDiv(at.y - rangeUnits, chunkSize).toInt()
    val lastChunkY = Math.floorDiv(at.y + rangeUnits, chunkSize).toInt()

    return (firstChunkX..lastChunkX).flatMap { chunkX ->
      (firstChunkY..lastChunkY).flatMap { chunkY -> structures.`in`(chunkX, chunkY) }
    }
  }

  private fun metresBetween(a: Vec3L, b: Vec3L): Double {
    return hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) * worldService.config.voxelSize
  }
}
