package net.bestia.zoneserver.actor.entity

import akka.actor.AbstractActor
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.model.geometry.Cube
import net.bestia.model.geometry.Shape

private val LOG = KotlinLogging.logger { }

data class OctreeEnvelope(
    val identifer: String,
    val payload: Any
) {
  companion object {
    fun fromDimension(rect: Cube, payload: Any): OctreeEnvelope {
      return OctreeEnvelope(
          identifer = "${rect.x}-${rect.y}-${rect.z}-${rect.width}-${rect.height}-${rect.depth}",
          payload = payload
      )
    }
  }
}

/**
 * Contains references to entities with a collision setup. This can be efficently used to fetch
 * all colliding entities.
 */
class OctreeActor : AbstractActor() {

  data class EntityCollision(
      val entityId: Long,
      val collisionShape: Shape
  )

  data class CheckCollision(
      val collision: Shape,
      val respondTo: ActorRef,
      val requestId: String
  )

  data class PutCollision(
      val collision: EntityCollision
  )

  data class RemoveCollision(
      val collision: EntityCollision
  )

  private class OctreeNode(
      val node: ActorRef,
      val dimension: Cube
  )

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(PutCollision::class.java, this::putCollisions)
        .build()
  }

  private var dimension: Cube = Cube(0, 0, 0)
  private var nodes: Array<OctreeNode>? = null
  private val collisions: MutableList<EntityCollision> = mutableListOf()

  private fun putCollisions(putCollision: PutCollision) {
    nodes?.also {
      for (i in 1..it.size) {
        if (it[i].dimension.collide(putCollision.collision.collisionShape)) {
          it[i].node.tell(putCollision, self)
          return
        }
      }
    }

    if (collisions.size > MAX_ENTITIES_PER_NODE) {
      splitNode()
    }
  }

  private fun removeCollision(removeCollision: RemoveCollision) {
    // Haben wir child nodes?  Ja -> removeCollision get an das passende child node
    // TODO
  }

  private fun splitNode() {
    // TODO
  }

  companion object {
    private const val MAX_ENTITIES_PER_NODE = 100
  }
}