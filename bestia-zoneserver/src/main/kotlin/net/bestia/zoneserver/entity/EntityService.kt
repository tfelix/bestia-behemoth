package net.bestia.zoneserver.entity

import akka.actor.PoisonPill
import mu.KotlinLogging
import net.bestia.messages.entity.EntityEnvelope
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.entity.component.Component
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * The [EntityService] is a central very important part of the bestia
 * game. It gives access to all entities in the game which represent all
 * interactive beeings with which the player can interact.
 *
 * @author Thomas Felix
 */
@Service
class EntityService(
    private val idGenerator: IdGeneratorService,
    private val messageApi: MessageApi
) {

  /**
   * Returns a fresh entity which can be used inside the system. It already
   * has a unique ID and can be used to persist date.
   *
   * @return A newly created entity.
   */
  fun newEntity(): Entity {
    return Entity(idGenerator.newId())
  }

  /**
   * Deletes the entity. If an entity has an active EntityActor the actor will
   * stop to operate if no more components are attached.
   *
   * @param entity The entity id remove from the memory database.
   */
  fun delete(entity: Entity) {
    delete(entity.id)
  }

  /**
   * Deletes the entity given by its id. This is a alias for
   * [.delete].
   *
   * @param entityId Removes this entity.
   */
  fun delete(entityId: Long) {
    LOG.trace { "Delete entity: $entityId" }
    val entityEnvelope = EntityEnvelope(entityId, PoisonPill.getInstance())
    messageApi.send(entityEnvelope)
  }

  fun updateComponent(component: Component) {
    val entityEnvelope = EntityEnvelope(component.entityId, component)
    messageApi.send(entityEnvelope)
  }
}
