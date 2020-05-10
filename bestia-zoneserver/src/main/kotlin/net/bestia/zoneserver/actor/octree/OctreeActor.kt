package net.bestia.zoneserver.actor.octree

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.javadsl.CommandHandler
import akka.persistence.typed.javadsl.Effect
import akka.persistence.typed.javadsl.EventHandler
import akka.persistence.typed.javadsl.EventSourcedBehavior
import mu.KotlinLogging
import net.bestia.model.geometry.Cube
import net.bestia.model.geometry.Shape

private val LOG = KotlinLogging.logger { }

data class EntityCollision(
    val entityId: Long,
    val collisionShape: Shape
)

/**
 * Contains references to entities with a collision setup. This can be efficiently used to fetch
 * all colliding entities.
 */
class OctreeActor2(
    private var dimension: Cube = Cube(0, 0, 0),
    persistenceId: PersistenceId
) : EventSourcedBehavior<OctreeActor2.OctreeCommand, OctreeActor2.OctreeEvent, OctreeActor2.CollisionState>(persistenceId) {
  interface OctreeEvent
  interface OctreeCommand

  data class CollisionState(
      val collisions: MutableList<EntityCollision> = mutableListOf(),
      var childs: List<ActorRef<OctreeCommand>>? = null
  )

  data class PutCollisionCommand(
      val entityId: Long,
      val collisionShape: Shape
  ) : OctreeCommand

  data class GetCollidingEntitiesCommand(
      val collision: Shape,
      val respondTo: ActorRef<OctreeEvent>,
      val requestId: String
  ) : OctreeCommand

  data class CollidingEntitiesEvent(
      val requestId: String,
      val collisions: List<EntityCollision>
  ) : OctreeEvent

  private data class AddCollidingEntityEvent(
      val entityId: Long,
      val collisionShape: Shape
  ) : OctreeEvent

  private data class RemoveCollidingEntityEvent(
      val entityId: Long
  ) : OctreeEvent

  private fun onGetCollisions(state: CollisionState, msg: GetCollidingEntitiesCommand): Effect<OctreeEvent, CollisionState> {
    val doCollide = state.collisions.filter { it.collisionShape.collide(msg.collision) }
    val response = CollidingEntitiesEvent(
        requestId = msg.requestId,
        collisions = doCollide
    )
    msg.respondTo.tell(response)

    return Effect().none()
  }

  private fun onPutCollision(state: CollisionState, msg: PutCollisionCommand): Effect<OctreeEvent, CollisionState> {
    // We discard collision commands if the requested entity does not match our dimension.
    if (!msg.collisionShape.collide(dimension)) {
      return Effect().none()
    }

    return Effect().persist(AddCollidingEntityEvent(msg.entityId, msg.collisionShape))
  }

  override fun emptyState(): CollisionState {
    return CollisionState()
  }

  override fun commandHandler(): CommandHandler<OctreeCommand, OctreeEvent, CollisionState> {
    return newCommandHandlerBuilder()
        .forAnyState()
        .onCommand(PutCollisionCommand::class.java, this::onPutCollision)
        .onCommand(GetCollidingEntitiesCommand::class.java, this::onGetCollisions)
        .build()
  }

  override fun eventHandler(): EventHandler<CollisionState, OctreeEvent> {
    return EventHandler{ state, event -> state }
  }

  companion object {
    fun create(dimension: Cube, persistenceId: PersistenceId): Behavior<OctreeCommand> {
      return Behaviors.setup { ctx -> OctreeActor2(dimension, persistenceId) }
    }

    val ENTITY_TYPE_KEY = EntityTypeKey.create(OctreeCommand::class.java, "Octree")
    private const val MAX_ENTITIES_PER_NODE = 100
  }
}