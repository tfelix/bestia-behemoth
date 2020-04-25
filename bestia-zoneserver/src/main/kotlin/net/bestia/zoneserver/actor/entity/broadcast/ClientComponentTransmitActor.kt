package net.bestia.zoneserver.actor.entity.broadcast

import akka.actor.AbstractActor
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import org.springframework.beans.factory.annotation.Qualifier

private val LOG = KotlinLogging.logger { }

/**
 * This actor decides of a component should be transmitted to one or multiple clients.
 * This is determined on a per component basis.
 */
@Actor
class ClientComponentTransmitActor(
    @Qualifier(BQualifier.CLIENT_FORWARDER)
    private val sendClient: ActorRef,
    private val transmitFilterService: TransmitFilterService
) : AbstractActor() {

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(TransmitRequest::class.java, this::broadcastComponent)
        .match(TransmitCommand::class.java, this::transmitToClients)
        .build()
  }

  private fun broadcastComponent(msg: TransmitRequest) {
    transmitFilterService.sendToReceivers(msg, context, self)
  }

  private fun transmitToClients(msg: TransmitCommand) {
    LOG.trace { "Sending component update ${msg.changedComponent.javaClass.simpleName} to: ${msg.receivingClientIds}" }

    msg.receivingClientIds.forEach {
      sendClient.tell(ClientEnvelope(it, msg.changedComponent), self)
    }
  }

  companion object {
    const val NAME = "clientComponentBroadcast"
  }
}
