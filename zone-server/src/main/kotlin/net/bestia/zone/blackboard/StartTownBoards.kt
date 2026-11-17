package net.bestia.zone.blackboard

import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.settlement.StartTownMarkets
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/**
 * Where the start towns' boards stand: beside the market hall's door, so new players find quests where they
 * already go to trade. The world makes no boards, so these are placed at every boot and never stored.
 */
@Component
class StartTownBoards(
  private val markets: StartTownMarkets,
  private val chunks: ChunkService,
) {

  fun spots(): List<Vec3L> {
    return markets.halls().mapNotNull { spotBeside(it.building.door, it.building.centre) }
  }

  private fun spotBeside(door: Vec2d, hallCentre: Vec2d): Vec3L? {
    val outward = (door - hallCentre).normalized()
    val spot = door + outward * STEP_OUT_METRES + outward.perpendicular() * STEP_ASIDE_METRES

    val config = chunks.config
    val x = (spot.x / config.voxelSize).roundToLong()
    val y = (spot.y / config.voxelSize).roundToLong()
    val ground = chunks.surfaceElevationAt(x, y) ?: return null

    return Vec3L(x, y, ChunkCoords.standingZ(config, ground))
  }

  private companion object {
    /** Clear of the doorstep, so the board blocks neither the door nor the path to it. */
    const val STEP_OUT_METRES = 1.5
    const val STEP_ASIDE_METRES = 3.0
  }
}
