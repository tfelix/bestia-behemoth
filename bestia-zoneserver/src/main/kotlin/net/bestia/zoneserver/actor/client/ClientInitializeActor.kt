package net.bestia.zoneserver.actor.client

import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import net.bestia.zoneserver.entity.PlayerEntityService
import org.springframework.beans.factory.annotation.Qualifier

data class InitializeClient(
    val accountId: Long
)

data class ClientInfoRequest(
    val accountId: Long
)

data class ClientInfoResponse(
    val bestiaSlotCount: Int,
    val masterBestiaEntityId: Long,
    val ownedBestiaEntityIds: List<Long>
)

private val LOG = KotlinLogging.logger { }

/**
 * Initializes a client connection if a client has newly connected.
 */
@Actor
class ClientInitializeActor(
    private val playerEntityService: PlayerEntityService,
    @Qualifier(BQualifier.CLIENT_FORWARDER)
    private val clientForwarder: ActorRef
) : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder
        .matchRedirect(InitializeClient::class.java, this::initializeClient)
        .matchRedirect(ClientInfoRequest::class.java, this::requestClientInfo)
  }

  private fun initializeClient(msg: InitializeClient) {
    LOG.trace { "Received: $msg" }
    playerEntityService.setDefaultActivePlayerBestia(msg.accountId)

    // TODO possibly spawn entities?
    val clientInfo = playerEntityService.getClientInfo(msg.accountId)
    val clientMsg = ClientEnvelope(msg.accountId, clientInfo)

    clientForwarder.tell(clientMsg, self)
  }

  private fun requestClientInfo(msg: ClientInfoRequest) {
    LOG.trace { "Received: $msg" }
  }

  companion object {
    const val NAME = "clientInit"
  }
}