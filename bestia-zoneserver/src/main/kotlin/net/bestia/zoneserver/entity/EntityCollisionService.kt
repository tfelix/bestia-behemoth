package net.bestia.zoneserver.entity

import net.bestia.model.geometry.CollisionShape
import org.springframework.stereotype.Service

@Service
class EntityCollisionService {

  fun updateEntityCollision(entityId: Long, shape: CollisionShape) {

  }

  fun getAllCollidingEntityIds(shape: CollisionShape): Set<Long> {
    return emptySet()
  }

  /*
    /**
   * Returns a list of account ids from players which active bestia entity is
   * inside the given rect. This is especially used and importand when update
   * messages must be send to all players inside a given area.
   *
   * @param range
   * @return
   */
  // TODO Das muss anders geregelt werden. Ggf muss hier ein Service/DB abgefragt werden.
  fun getActiveAccountIdsInRange(range: Rect): List<Long> {

    val entitiesInRange = entitySearchService.getCollidingEntities(range)

    LOG.trace("Entities in range: {}", entitiesInRange)
    // Filter only for active entities.
    val activeAccountIds = entitiesInRange.stream()
        .filter { entity -> isActiveEntity(entity.id) }
        .map<Any> { entity ->
          entityService.getComponent(entity, PlayerComponent::class.java)
              .map(???({ it.getOwnerAccountId() }))
          .orElse(0L)
        }
        .filter { id -> id != 0 }
        .collect<List<Long>, Any>(Collectors.toList<Any>())

    LOG.trace("Active player entities {} in range: {}", activeAccountIds, range)

    return activeAccountIds
  }
   */
}