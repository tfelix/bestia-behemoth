package net.bestia.zoneserver.entity

import net.bestia.model.entity.EntityPositionRepository
import net.bestia.model.geometry.Shape
import org.springframework.stereotype.Service
import java.lang.IllegalStateException

@Service
class EntityCollisionService {

  private val cache = mutableMapOf<Long, Shape>()

  fun updateEntityCollision(entityId: Long, shape: Shape) {
    cache[entityId] = shape
  }

  /*
  fun getAllCollidingEntityIds(shape: Shape): Set<Long> {
    val bbox = shape.boundingBox
    val dX = bbox.x + bbox.width
    val dY = bbox.y + bbox.depth
    val dZ = bbox.z + bbox.height

    return entityPositionRepository.findAllInside(bbox.x, bbox.y, bbox.z, dX, dY, dZ)
        .map { it.entityId }
        .toSet()
  }*/

  fun getAllCollidingEntityIds(shape: Shape): Set<Long> {
    val colliding = mutableSetOf<Long>()
    cache.forEach {
      if (it.value.collide(shape)) {
        colliding.add(it.key)
      }
    }

    return colliding
  }

  fun getAllCollidingEntityIds(shapes: List<Shape>): Set<Long> {
    return shapes.flatMap { getAllCollidingEntityIds(it) }.toSet()
  }
}