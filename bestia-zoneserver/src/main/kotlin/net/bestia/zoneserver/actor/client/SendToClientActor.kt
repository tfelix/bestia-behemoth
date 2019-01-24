package net.bestia.zoneserver.actor.client

import akka.actor.AbstractActor
import akka.cluster.sharding.ClusterSharding
import net.bestia.zoneserver.EntryActorNames
import mu.KotlinLogging
import net.bestia.messages.AccountMessage
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.actor.ActorComponentNoComponent

private val LOG = KotlinLogging.logger { }

/**
 * This actor sends the incoming message towards the registered cluster sharding
 * actor which holds the connection to a client.
 *
 * @author Thomas Felix
 */
@ActorComponentNoComponent
class SendToClientActor : AbstractActor() {

  private var clientConnection = ClusterSharding.get(context.system)
      .shardRegion(EntryActorNames.SHARD_CONNECTION)

  override fun createReceive(): AbstractActor.Receive {
    return receiveBuilder()
        .match(AccountMessage::class.java, this::handleAccountMessage)
        .match(ClientEnvelope::class.java, this::handleClientEnvelope)
        .build()
  }

  private fun handleClientEnvelope(msg: ClientEnvelope) {
    clientConnection.tell(msg, self)
  }

  private fun handleAccountMessage(msg: AccountMessage) {
    LOG.debug("Sending to client: {}", msg)
    val accountId = msg.accountId
    clientConnection.tell(ClientEnvelope(accountId, msg), sender)
  }

  companion object {
    const val NAME = "sendToClient"
  }
}
