package net.bestia.zoneserver.entity

import mu.KotlinLogging
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.model.geometry.CollisionShape
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

@Service
class EntitySearchService {

  /**
   * Returns all entities which are currently colliding with the given entity.
   * The entity to check for collisions must implement the position component.
   * Also only entities implementing position components can be checked
   * against collision. If the entity does not have a
   * {@link PositionComponent} an empty set will be returned.
   * <p>
   * This is basically a shortcut for a rather frequently called operation for
   * scripts to get entities colliding with script entities. It is similar to
   * {@link #getCollidingEntities(CollisionShape)}.
   *
   * @return All entities colliding with this entity.
   */
  fun getCollidingEntities(entity: Entity): Set<Entity> {
    LOG.trace { "Finding all colliding entities for: $entity." }

    val posComp = entityService.getComponent(entity, PositionComponent::class.java)

    return if (posComp.isPresent) {
      val shape = posComp.get().shape
      val collidingEntities = getCollidingEntities(shape)

      LOG.trace { "Found colliding entities: $collidingEntities" }
      return collidingEntities
    } else {
      hashSetOf()
    }
  }

  /**
   * Returns all the entities which are in range. The detected collision
   * entities will have a {@link PositionComponent} for sure. Other components
   * are optional.
   *
   * @param area The area in which the looked up entities lie.
   * @return All entities contained in the area.
   */
  fun getCollidingEntities(area: CollisionShape): Set<Entity> {

    val colliders = mutableSetOf<Entity>()

    entities.forEach { _, entity ->
      run {
        entityService.getComponent(entity, PositionComponent::class.java).ifPresent {
          if (it.shape.collide(area)) {
            colliders.add(entity)
          }
        }
      }
    }

    return colliders
  }
}