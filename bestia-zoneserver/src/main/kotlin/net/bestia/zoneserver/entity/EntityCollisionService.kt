package net.bestia.zoneserver.entity

import mu.KotlinLogging
import net.bestia.model.geometry.Shape
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

@Service
class EntityCollisionService {

  private val cache = mutableMapOf<Long, Shape>()

  fun updateEntityCollision(entityId: Long, shape: Shape) {
    LOG.trace { "updateEntityCollision - entityId: $entityId, shape: $shape" }
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