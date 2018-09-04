package net.bestia.zoneserver.actor.client

import akka.actor.AbstractActor
import akka.cluster.sharding.ClusterSharding
import bestia.server.EntryActorNames
import mu.KotlinLogging
import net.bestia.messages.JsonMessage
import net.bestia.messages.client.ToClientEnvelope
import net.bestia.messages.component.LatencyInfo
import net.bestia.zoneserver.client.LatencyService
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

private val LOG = KotlinLogging.logger { }

/**
 * This actor sends the incoming message towards the registered cluster sharding
 * actor which holds the connection to a client.
 *
 * @author Thomas Felix
 */
@Component
@Scope("prototype")
class SendToClientActor(
        private val latencyService: LatencyService
) : AbstractActor() {

  private var clientConnection = ClusterSharding.get(context.system).shardRegion(EntryActorNames.SHARD_CONNECTION)

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
            .match(JsonMessage::class.java, this::handleSendClient)
            .build()
  }

  private fun handleSendClient(msg: JsonMessage) {
    LOG.debug("Sending to client: {}", msg)

    if (msg is LatencyInfo) {
      // If its a component message include the client latency in the
      // message because the clients might need this for animation
      // critical data.
      val latency = latencyService.getClientLatency(msg.accountId)
      val updatedMsg = (msg as LatencyInfo).createNewInstance(msg.accountId, latency)
      val envelope = ToClientEnvelope(msg.accountId, updatedMsg)
      clientConnection.tell(envelope, sender)
    } else {
      val envelope = ToClientEnvelope(msg.accountId, msg)
      clientConnection.tell(envelope, sender)
    }
  }

  companion object {
    const val NAME = "sendToClient"
  }
}
