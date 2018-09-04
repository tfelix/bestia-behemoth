package net.bestia.zoneserver.actor.routing

import akka.cluster.sharding.ShardRegion
import net.bestia.messages.client.ToClientEnvelope
import net.bestia.zoneserver.actor.connection.ClientConnectionActor
import org.springframework.stereotype.Component

/**
 * Defines methods for extracting the shard id from the incoming messages for
 * client connection actors.
 *
 * @author Thomas Felix
 */
@Component
class ConnectionShardMessageExtractor : ShardRegion.MessageExtractor {

  override fun entityId(message: Any): String? {
    return if (message is ToClientEnvelope) {
      val accId = message.accountId
      getActorName(accId)
    } else {
      null
    }
  }

  private fun getActorName(accId: Long): String? {
    return if (accId <= 0) {
      null
    } else ClientConnectionActor.getActorName(accId)
  }

  override fun entityMessage(message: Any): Any {
    // The message should be resend as is without altering it.
    return message
  }

  override fun shardId(message: Any): String? {
    return if (message is ToClientEnvelope) {
      val accId = message.accountId
      getShardId(accId)
    } else {
      null
    }
  }

  private fun getShardId(accId: Long): String? {
    if (accId <= 0) {
      return null
    }
    val name = ClientConnectionActor.getActorName(accId)
    return (name.hashCode() % NUMBER_OF_SHARDS).toString()
  }

  companion object {
    private const val NUMBER_OF_SHARDS = 10
  }
}