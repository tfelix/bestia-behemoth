package net.bestia.zoneserver.actor.client

import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.AccountMessage
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import org.springframework.beans.factory.annotation.Qualifier

data class PingRequest(
    val accountId: Long,
    val sequenceNumber: Long
)

data class PingResponse(
    override val accountId: Long,
    val sequenceNumber: Long
) : AccountMessage

private val LOG = KotlinLogging.logger { }

/**
 * Initializes a client connection if a client has newly connected.
 * Is also responsible for setting up the player entities.
 */
@Actor
class ClientPingActor(
    @Qualifier(BQualifier.CLIENT_FORWARDER)
    private val clientForwarder: ActorRef
) : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder
        .matchRedirect(PingRequest::class.java, this::ping)
  }

  private fun ping(msg: PingRequest) {
    LOG.trace { "Received: $msg" }

    val response = PingResponse(accountId = msg.accountId, sequenceNumber = msg.sequenceNumber)

    clientForwarder.tell(response, self)
  }

  companion object {
    const val NAME = "clientPing"
  }
}