package net.bestia.zoneserver.actor.routing

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Terminated
import net.bestia.messages.AccountMessage
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

data class RegisterRouteMessage(
        val handlesMessage: (message: AccountMessage) -> Boolean,
        val receiverActor: ActorRef
)

/**
 * The postmaster actor sorts incoming messages for the envelope and will deliver them to all
 * subscribed actors.
 */
@Component
@Scope("prototype")
class RouterActor : AbstractActor() {

  private val routees = mutableListOf<RegisterRouteMessage>()

  override fun createReceive(): Receive {
    return receiveBuilder()
            .match(AccountMessage::class.java, this::handleEnvelope)
            .match(RegisterRouteMessage::class.java, this::registerListener)
            .match(Terminated::class.java, this::removeTerminatedActor)
            .build()
  }

  private fun removeTerminatedActor(msg: Terminated) {
    val actorRef = msg.actor
    routees.removeIf { it.receiverActor == actorRef }
  }

  private fun registerListener(msg: RegisterRouteMessage) {
    routees.add(msg)
    context.watch(msg.receiverActor)
  }

  private fun handleEnvelope(msg: AccountMessage) {
    routees.filter { it.handlesMessage(msg) }.forEach { it.receiverActor.tell(msg, self) }
  }
}