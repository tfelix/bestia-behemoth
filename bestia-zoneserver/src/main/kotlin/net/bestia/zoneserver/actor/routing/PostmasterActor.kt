package net.bestia.zoneserver.actor.routing

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Terminated
import net.bestia.messages.Envelope
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

class RegisterEnvelopeMessage(
        val envelopeName: String,
        val receiverActor: ActorRef
)

/**
 * The postmaster actor sorts incoming messages for the envelope and will deliver them to all
 * subscribed actors.
 */
@Component
@Scope("prototype")
class PostmasterActor : AbstractActor() {

  private val listener = mutableMapOf<String, MutableList<ActorRef>>()

  override fun createReceive(): Receive {
    return receiveBuilder()
            .match(Envelope::class.java, this::handleEnvelope)
            .match(RegisterEnvelopeMessage::class.java, this::registerListener)
            .match(Terminated::class.java, this::removeTerminatedActor)
            .build()
  }

  private fun removeTerminatedActor(msg: Terminated) {
    val actorRef = msg.actor
    listener.filter { it.value.contains(actorRef) }.map { it. }
  }

  private fun registerListener(msg: RegisterEnvelopeMessage) {
    val list = listener[msg.envelopeName]

    if(list != null) {
      list.add(msg.receiverActor)
    } else {
      listener[msg.envelopeName] = mutableListOf(msg.receiverActor)
    }

    context.watch(msg.receiverActor)
  }

  private fun handleEnvelope(envelope: Envelope) {
    listener.get(envelope.identifier).let {
      val content = envelope.content
      it?.forEach { it.forward(content, context) }
    }
  }
}