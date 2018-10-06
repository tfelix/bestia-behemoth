package net.bestia.zoneserver.actor.routing

import akka.actor.AbstractActor
import net.bestia.messages.client.ClientEnvelope
import net.bestia.messages.entity.EntityEnvelope
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.SendToClientActor
import net.bestia.zoneserver.actor.entity.SendToEntityActor
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope("prototype")
class RoutingActor : AbstractActor() {

  private val sendToClientActor = SpringExtension.actorOf(context, SendToClientActor::class.java)
  private val sendToEntityActor = SpringExtension.actorOf(context, SendToEntityActor::class.java)

  override fun createReceive(): Receive {
    return receiveBuilder()
            .match(EntityEnvelope::class.java, this::handleToEntity)
            .match(ClientEnvelope::class.java, this::handleToClient)
            .build()
  }

  private fun handleToClient(msg: ClientEnvelope) {
    sendToClientActor.forward(msg, context)
  }

  private fun handleToEntity(msg: EntityEnvelope) {
    sendToEntityActor.forward(msg, context)
  }

  companion object {
    const val NAME = "routing"
  }
}