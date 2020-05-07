package net.bestia.zoneserver.actor.entity

import akka.actor.ActorRef
import akka.actor.ActorRefFactory
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
  val requestMsg = EntityRequest(requestActor)

  entitiyIds.forEach { entityId -> messageApi.send(EntityEnvelope(entityId, requestMsg)) }
}