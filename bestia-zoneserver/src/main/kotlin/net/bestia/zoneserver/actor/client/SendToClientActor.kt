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
            .match(ToClientEnvelope::class.java, this::sendToClient)
            .build()
  }

  private fun sendToClient(msg: ToClientEnvelope) {
    clientConnection.tell(msg, self)
  }

  private fun handleSendClient(msg: JsonMessage) {
    LOG.debug("Sending to client: {}", msg)
    val accountId = msg.accountId
    when (msg) {
      is LatencyInfo -> addLatencyInfoToMessage(accountId, msg)
      else -> clientConnection.tell(ToClientEnvelope(accountId, msg), sender)
    }
  }

  /**
   * If its a component message include the client latency in the
   * message because the clients might need this for animation
   * critical data.
   */
  private fun addLatencyInfoToMessage(accountId: Long, msg: LatencyInfo) {
    // This is a good example. the master entity should have a latency component which should be fetched
    // for this opportunity from the entity component
    // TODO The latency data should be managed on the ClientConnectionActor.
    val latency = latencyService.getClientLatency(accountId)
    val updatedMsg = msg.createNewInstance(accountId, latency)
    val envelope = ToClientEnvelope(accountId, updatedMsg)
    clientConnection.tell(envelope, sender)
  }

  companion object {
    const val NAME = "sendToClient"
  }
}
