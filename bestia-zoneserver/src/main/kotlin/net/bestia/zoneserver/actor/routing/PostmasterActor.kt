package net.bestia.zoneserver.actor.routing

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Terminated
import net.bestia.messages.Envelope
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

data class RegisterEnvelopeMessage(
        val envelopeClass: Class<out Envelope>,
        val receiverActor: ActorRef
)

/**
 * The postmaster actor sorts incoming messages for the envelope and will deliver them to all
 * subscribed actors.
 */
@Component
@Scope("prototype")
class PostmasterActor : AbstractActor() {

  private val listener = mutableMapOf<Class<out Envelope>, MutableList<ActorRef>>()

  override fun createReceive(): Receive {
    return receiveBuilder()
            .match(Envelope::class.java, this::handleEnvelope)
            .match(RegisterEnvelopeMessage::class.java, this::registerListener)
            .match(Terminated::class.java, this::removeTerminatedActor)
            .build()
  }

  private fun removeTerminatedActor(msg: Terminated) {
    val actorRef = msg.actor
    listener.values.find { it.contains(actorRef) }?.remove(actorRef)
  }

  private fun registerListener(msg: RegisterEnvelopeMessage) {
    if(listener.containsKey(msg.envelopeClass)) {
      listener[msg.envelopeClass]!!.add(msg.receiverActor)
    } else {
      listener[msg.envelopeClass] = mutableListOf(msg.receiverActor)
    }

    context.watch(msg.receiverActor)
  }

  private fun handleEnvelope(envelope: Envelope) {
    listener[envelope.javaClass]?.let {
      val content = envelope.content
      it.forEach { it.forward(content, context) }
    }
  }

  companion object {
    const val NAME = "postmaster"
  }
}