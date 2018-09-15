package net.bestia.zoneserver.actor.connection

import akka.cluster.sharding.ClusterSharding
import net.bestia.zoneserver.EntryActorNames
import mu.KotlinLogging
import net.bestia.messages.client.ClientConnectMessage
import net.bestia.messages.client.ToClientEnvelope
import net.bestia.zoneserver.actor.routing.BaseClientMessageRouteActor
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * Handling new or closing connection is a bit hard to understand: Messages are coming into the web ingest and need
 * to be directed towards the client connection actor who is handles via sharding. So we basically send this message
 * to the client who will work upon this status message.
 */
@Component
@Scope("prototype")
class ClientConnectionManagerActor : BaseClientMessageRouteActor() {

  private val clientConnectionActor = ClusterSharding.get(context.system)
          .shardRegion(EntryActorNames.SHARD_CONNECTION)

  override fun createReceive(builder: BuilderFacade) {
    builder.match(ClientConnectMessage::class.java, this::onClientConnection)
  }

  private fun onClientConnection(msg: ClientConnectMessage) {
    LOG.trace { "Received: $msg" }
    val envelope = ToClientEnvelope(msg.accountId, msg)
    clientConnectionActor.tell(envelope, self)
  }

  companion object {
    const val NAME = "clientConnectionManager"
  }
}