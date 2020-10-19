package net.bestia.zoneserver.actor.entity.transmit

import akka.actor.AbstractActor
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import net.bestia.zoneserver.actor.entity.awaitEntityResponse
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.entity.component.Component
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
    private val messageApi: MessageApi,
    private val transmitFilterService: TransmitFilterService
) : AbstractActor() {

  override fun createReceive(): Receive {
    return receiveBuilder()
        .match(TransmitRequest::class.java, this::broadcastComponent)
        .build()
  }

  private fun broadcastComponent(msg: TransmitRequest) {
    LOG.trace { "Received: $msg" }
    val candidateEntityIds = transmitFilterService.findTransmitCandidates(msg)

    awaitEntityResponse(messageApi, context, candidateEntityIds) { entities ->
      val accountIds = transmitFilterService.selectTransmitCandidates(entities.all.toSet(), msg)
      transmitToClients(accountIds, msg.changedComponent)
    }
  }

  private fun transmitToClients(accountIds: Set<Long>, changedComponent: Component) {
    LOG.trace { "Sending component update $changedComponent to: $accountIds" }

    accountIds.forEach {
      sendClient.tell(ClientEnvelope(it, changedComponent), self)
    }
  }

  companion object {
    const val NAME = "clientComponentBroadcast"
  }
}
