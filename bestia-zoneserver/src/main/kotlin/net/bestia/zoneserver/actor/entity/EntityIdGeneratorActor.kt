package net.bestia.zoneserver.actor.entity

import akka.actor.AbstractActor
import akka.actor.ActorRef
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

data class NewEntityIdRequest(
        val respondTo: ActorRef
)

data class NewEntityIdResponse(
        val entityId: Long
)

@Component
@Scope("prototype")
class EntityIdGeneratorActor : AbstractActor() {

  var currentEntityId = 1L

  override fun createReceive(): Receive {
    return receiveBuilder()
            .match(NewEntityIdRequest::class.java, this::requestEntityId)
            .build()
  }

  private fun requestEntityId(msg: NewEntityIdRequest) {
    val response = NewEntityIdResponse(currentEntityId++)
    msg.respondTo.tell(response, self)
  }

  companion object {
    const val NAME = "entityIdGenerator"
  }
}