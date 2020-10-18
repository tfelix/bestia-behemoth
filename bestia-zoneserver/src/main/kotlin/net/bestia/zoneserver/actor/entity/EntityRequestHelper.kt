package net.bestia.zoneserver.actor.entity

import akka.actor.ActorRef
import akka.actor.ActorRefFactory
import net.bestia.messages.entity.EntityMessage
import net.bestia.zoneserver.actor.AwaitResponseActor
import net.bestia.zoneserver.actor.Responses
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.entity.Entity
import java.util.*

/**
 * This message is send back if the requested entity does not exist.
 */
object EntityDoesNotExist

data class EntityRequest(
    override val entityId: Long,
    val replyTo: ActorRef,
    val context: Any? = null
) : EntityMessage

/**
 * Is send directly to the entity from one of its child.
 * The entity ID can therefore be omitted.
 */
data class LocalEntityRequest(
    val replyTo: ActorRef,
    val context: Any? = null
)

fun awaitEntityResponse(
    messageApi: MessageApi,
    ctx: ActorRefFactory,
    entityId: Long,
    callback: (Entity) -> Unit
) {
  awaitEntityResponse(messageApi, ctx, setOf(entityId)) {
    it[entityId].let(callback)
  }
}

fun awaitEntityResponse(
    messageApi: MessageApi,
    ctx: ActorRefFactory,
    entitiyIds: Set<Long>,
    callback: (EntitiesResponse) -> Unit
) {
  if (entitiyIds.isEmpty()) {
    callback(EntitiesResponse(emptyMap()))
  }

  val hasReceivedAll = { responses: List<Any> ->
    responses.filterIsInstance(EntityResponse::class.java)
        .map { it.entity.id }
        .toSet() == entitiyIds
  }
  val transformResponse = { response: Responses ->
    val mappedEntities = response.receivedResponses
        .filterIsInstance(EntityResponse::class.java)
        .map { it.entity.id to it.entity }
        .toMap()
    callback(EntitiesResponse(mappedEntities))
  }
  val props = AwaitResponseActor.props(checkResponseReceived = hasReceivedAll, action = transformResponse)
  val requestActor = ctx.actorOf(props, "awaitrespo-entity-${UUID.randomUUID()}")

  entitiyIds.forEach { entityId -> messageApi.send(EntityRequest(entityId, requestActor)) }
}