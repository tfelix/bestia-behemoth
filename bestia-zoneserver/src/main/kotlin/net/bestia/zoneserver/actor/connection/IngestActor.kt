package net.bestia.zoneserver.actor.connection

import akka.actor.AbstractActor
import mu.KotlinLogging
import net.bestia.messages.entity.EntityEnvelope
import net.bestia.zoneserver.actor.SpringExtension
import net.bestia.zoneserver.actor.client.ClientMessageActor
import net.bestia.zoneserver.actor.entity.SendToEntityActor
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Central influx point for web clients. The incoming messages are resend
 * towards the connection actors which manage the client connections.
 *
 * TODO Its now not clear if this is still needed if we have direct connection into
 * the zone actor.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class IngestActor : AbstractActor() {

  private val sendToEntity = SpringExtension.actorOf(context, SendToEntityActor::class.java)
  private val clientMessageActor = SpringExtension.actorOf(context, ClientMessageActor::class.java)

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder().matchAny(this::handleClientMessage).build()
  }

  private fun handleClientMessage(message: Any) {
    LOG.debug { "Received message from web: $message" }

    // TODO Handle the REST Calls here
    when (message) {
      is EntityEnvelope -> sendToEntity.tell(message, sender)
      else -> clientMessageActor.tell(message, sender)
    }
  }

  companion object {
    const val NAME = "ingest"
  }
}
