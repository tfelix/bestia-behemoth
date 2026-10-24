package net.bestia.zone.companion.query

import net.bestia.bus.contract.MapPoint
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component

/** Puts a voxel position on the map: east and north in metres, the axes the map's tiles are addressed in. */
@Component
class MapPlane(private val worldService: WorldService) {

  fun eastMetres(position: Vec3L): Double {
    return position.x * worldService.config.voxelSize
  }

  fun northMetres(position: Vec3L): Double {
    return position.y * worldService.config.voxelSize
  }

  /** A speed in voxels a second, the unit the movement system walks in, as metres a second. */
  fun metresPerSecond(voxelsPerSecond: Float): Double {
    return voxelsPerSecond * worldService.config.voxelSize
  }

  fun pointOf(position: Vec3L): MapPoint {
    return MapPoint(eastMetres(position), northMetres(position))
  }

  /** The column under a map point. The height is left to whoever walks there, which finds the ground's. */
  fun positionAt(point: MapPoint): Vec3L {
    val voxelSize = worldService.config.voxelSize
    return Vec3L(Math.round(point.east / voxelSize), Math.round(point.north / voxelSize), 0)
  }
}
