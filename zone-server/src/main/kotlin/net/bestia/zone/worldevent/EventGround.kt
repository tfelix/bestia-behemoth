package net.bestia.zone.worldevent

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.stream.ChunkCoords
import net.bestia.zone.world.stream.ChunkService
import org.springframework.stereotype.Component

/** Where an event's creature can stand. */
@Component
class EventGround(
  private val chunks: ChunkService,
  private val worldService: WorldService,
) {

  /** The voxel a creature stands on at [x], [y] metres, or null on water or where the ground is not known. */
  fun grounded(x: Double, y: Double): Vec3L? {
    val config = worldService.config
    val voxelX = Math.floor(x / config.voxelSize).toLong()
    val voxelY = Math.floor(y / config.voxelSize).toLong()
    val elevation = chunks.surfaceElevationAt(voxelX, voxelY) ?: return null
    if (elevation <= config.seaLevel) return null
    return Vec3L(voxelX, voxelY, ChunkCoords.standingZ(config, elevation))
  }
}
