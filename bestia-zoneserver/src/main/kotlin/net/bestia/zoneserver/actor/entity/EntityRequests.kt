package net.bestia.zoneserver.actor.entity

import akka.actor.ActorContext
import akka.actor.ActorRef
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.actor.AwaitResponseActor
import net.bestia.zoneserver.actor.Responses
import net.bestia.zoneserver.entity.Entity
import java.lang.IllegalArgumentException

data class EntityRequest(
    val replyTo: ActorRef,
    val context: Any? = null
)

data class EntitiesResponse(
    private val data: Map<Long, Entity>
) {
  operator fun get(key: Long): Entity {
    return data[key]
        ?: throw IllegalArgumentException("Entity with id $key was not inside response.")
  }

  val all get() = data.values
}

fun awaitEntityResponse(
    messageApi: MessageApi,
    ctx: ActorContext,
    entityId: Long,
    callback: (Entity) -> Unit
) {
  awaitEntityResponse(messageApi, ctx, setOf(entityId)) {
    it[entityId].let(callback)
  }
}

fun awaitEntityResponse(
    messageApi: MessageApi,
    ctx: ActorContext,
    entitiyIds: Set<Long>,
    callback: (EntitiesResponse) -> Unit
) {
  val hasReceivedAll: (List<Any>) -> Boolean = { responses: List<Any> ->
    responses.toSet() == entitiyIds
  }
  val transformResponse = { response: Responses ->
    val mappedEntities = response.getAllResponses(Entity::class).map { it.id to it }.toMap()
    callback(EntitiesResponse(mappedEntities))
  }
  val props = AwaitResponseActor.props(hasReceivedAll, transformResponse)
  val requestActor = ctx.actorOf(props)
  val requestMsg = EntityRequest(requestActor)
  entitiyIds.forEach { messageApi.send(EntityEnvelope(it, requestMsg)) }
}