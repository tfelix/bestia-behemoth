package net.bestia.zoneserver.entity

import net.bestia.model.geometry.Shape
import org.springframework.stereotype.Service

@Service
class EntityCollisionService {

  private val cache = mutableMapOf<Long, Shape>()

  fun updateEntityCollision(entityId: Long, shape: Shape) {
    cache[entityId] = shape
  }

  fun getAllCollidingEntityIds(shape: Shape): Set<Long> {
    val colliding = mutableSetOf<Long>()
    cache.forEach {
      if (it.value.collide(shape)) {
        colliding.add(it.key)
      }
    }

    return colliding
  }
}