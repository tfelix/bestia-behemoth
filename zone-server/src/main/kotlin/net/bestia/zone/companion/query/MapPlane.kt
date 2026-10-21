package net.bestia.zone.companion.query

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
}
