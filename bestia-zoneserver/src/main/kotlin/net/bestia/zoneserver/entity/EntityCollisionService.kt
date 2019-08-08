package net.bestia.zoneserver.entity

import net.bestia.model.geometry.Shape
import org.springframework.stereotype.Service

@Service
class EntityCollisionService {

  fun updateEntityCollision(entityId: Long, shape: Shape) {

  }

  fun getAllCollidingEntityIds(shape: Shape): Set<Long> {
    return emptySet()
  }

  fun getAllCollidingEntityIds(shapes: List<Shape>): Set<Long> {
    return emptySet()
  }
}