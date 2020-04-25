package net.bestia.zoneserver.map

import net.bestia.model.geometry.Cube
import net.bestia.model.geometry.Vec3

class MapService {

  companion object {
    fun getUpdateRect(position: Vec3): Cube {
      // TODO This is currently a guessed value
      val rectSize = 20
      return Cube(
          position.x - rectSize,
          position.y - rectSize,
          position.z - rectSize,
          position.x + rectSize,
          position.y + rectSize,
          position.z + rectSize
      )
    }
  }
}