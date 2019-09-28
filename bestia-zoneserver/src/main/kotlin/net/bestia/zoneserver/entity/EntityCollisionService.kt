package net.bestia.zoneserver.entity

import net.bestia.model.entity.EntityPositionRepository
import net.bestia.model.geometry.Shape
import org.springframework.stereotype.Service
import java.lang.IllegalStateException

@Service
class EntityCollisionService(
    private val entityPositionRepository: EntityPositionRepository
) {

  fun updateEntityCollision(entityId: Long, shape: Shape) {
    throw IllegalStateException("not implemented yet")
  }

  fun getAllCollidingEntityIds(shape: Shape): Set<Long> {
    val bbox = shape.boundingBox
    val dX = bbox.x + bbox.width
    val dY = bbox.y + bbox.depth
    val dZ = bbox.z + bbox.height

    return entityPositionRepository.findAllInside(bbox.x, bbox.y, bbox.z, dX, dY, dZ)
        .map { it.entityId }
        .toSet()
  }

  fun getAllCollidingEntityIds(shapes: List<Shape>): Set<Long> {
    return shapes.flatMap { getAllCollidingEntityIds(it) }.toSet()
  }
}