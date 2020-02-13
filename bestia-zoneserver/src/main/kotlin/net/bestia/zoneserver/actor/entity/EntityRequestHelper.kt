package net.bestia.zoneserver.actor.entity

import akka.actor.ActorRef
import akka.actor.ActorRefFactory
import net.bestia.zoneserver.actor.AwaitResponseActor
import net.bestia.zoneserver.actor.Responses
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.entity.Entity

/**
 * Message is send back if the requested entity does not exist.
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
    responses.toSet() == entitiyIds
  }
  val transformResponse = { response: Responses ->
    val mappedEntities = response.receivedResponses
        .filterIsInstance(Entity::class.java)
        .map { it.id to it }.toMap()
    callback(EntitiesResponse(mappedEntities))
  }
  val props = AwaitResponseActor.props(checkResponseReceived = hasReceivedAll, action = transformResponse)
  val requestActor = ctx.actorOf(props)
  val requestMsg = EntityRequest(requestActor)
  entitiyIds.forEach { messageApi.send(EntityEnvelope(it, requestMsg)) }
}