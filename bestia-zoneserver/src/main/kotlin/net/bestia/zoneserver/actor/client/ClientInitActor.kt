package net.bestia.zoneserver.actor.client

import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.actor.Actor
import net.bestia.zoneserver.actor.BQualifier
import net.bestia.zoneserver.actor.entity.NewEntity
import net.bestia.zoneserver.actor.routing.DynamicMessageRoutingActor
import org.springframework.beans.factory.annotation.Qualifier

data class InitializeClient(
    val accountId: Long
)

data class ClientInfoRequest(
    val accountId: Long
)

data class ClientInfoResponse(
    val bestiaSlotCount: Int,
    /**
     * Master can be null, the client must then first start to create
     * a account.
     */
    val masterBestiaEntityId: Long?,
    val activeEntityId: Long,
    val ownedBestias: List<OwnedBestias>
) {
  data class OwnedBestias(
      val entityId: Long,
      val playerBestiaId: Long
  )
}

private val LOG = KotlinLogging.logger { }

/**
 * Initializes a client connection if a client has newly connected.
 * Is also responsible for setting up the player entities.
 */
@Actor
class ClientInitActor(
    private val clientInitService: ClientInitService,
    private val clientInfoService: ClientInfoService,
    @Qualifier(BQualifier.CLIENT_FORWARDER)
    private val clientForwarder: ActorRef,
    @Qualifier(BQualifier.ENTITY_FORWARDER)
    private val entityForwarder: ActorRef
) : DynamicMessageRoutingActor() {

  override fun createReceive(builder: BuilderFacade) {
    builder
        .matchRedirect(InitializeClient::class.java, this::initializeClient)
        .matchRedirect(ClientInfoRequest::class.java, this::requestClientInfo)
  }

  private fun initializeClient(msg: InitializeClient) {
    LOG.trace { "Received: $msg" }
    val initResult = clientInitService.setupDefaultActivePlayerBestia(msg.accountId)

    if (initResult is InitResultNewEntity) {
      entityForwarder.tell(
          NewEntity(initResult.spawnedActiveEntity),
          self
      )
    }

    val clientInfo = clientInfoService.getClientInfo(msg.accountId)
    val clientMsg = ClientEnvelope(msg.accountId, clientInfo)

    clientForwarder.tell(clientMsg, self)
  }

  private fun requestClientInfo(msg: ClientInfoRequest) {
    LOG.trace { "Received: $msg" }

    val clientInfo = clientInfoService.getClientInfo(msg.accountId)
    val clientMsg = ClientEnvelope(msg.accountId, clientInfo)

    clientForwarder.tell(clientMsg, self)
  }

  companion object {
    const val NAME = "clientInit"
  }
}