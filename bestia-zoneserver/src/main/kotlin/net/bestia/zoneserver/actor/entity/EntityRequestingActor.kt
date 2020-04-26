package net.bestia.zoneserver.actor.entity

import akka.actor.AbstractActorWithTimers
import net.bestia.zoneserver.actor.AwaitResponseActor
import net.bestia.zoneserver.actor.Responses
import net.bestia.zoneserver.entity.Entity

abstract class EntityRequestingActor : AbstractActorWithTimers() {
  protected fun requestOwnerEntity(
      callback: (Entity) -> Unit
  ) {
    val hasReceived: (List<Any>) -> Boolean = {
      true
    }
    val transformResponse = { response: Responses ->
      val entityResponse = response.getResponse(EntityResponse::class)
      callback(entityResponse.entity)
    }
    val props = AwaitResponseActor.props(
        checkResponseReceived = hasReceived,
        action = transformResponse
    )
    val requestActor = context.actorOf(props)
    val requestMsg = EntityRequest(requestActor)
    context.parent.tell(requestMsg, requestActor)
  }
}