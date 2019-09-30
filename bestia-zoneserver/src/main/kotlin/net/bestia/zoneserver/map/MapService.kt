package net.bestia.zoneserver.map

import net.bestia.model.geometry.Rect
import net.bestia.model.geometry.Vec3

class MapService {

  companion object {
    fun getUpdateRect(position: Vec3): Rect {
      // TODO This is currently a guessed value
      val rectSize = 20
      return Rect(
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