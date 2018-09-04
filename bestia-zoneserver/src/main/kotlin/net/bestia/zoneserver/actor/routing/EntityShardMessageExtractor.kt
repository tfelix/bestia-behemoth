package net.bestia.zoneserver.actor.routing

import akka.cluster.sharding.ShardRegion
import net.bestia.messages.entity.ToEntityEnvelope

/**
 * Defines methods for extracting the shard id from the incoming messages for
 * entity actors.
 *
 * @author Thomas Felix
 */
class EntityShardMessageExtractor : ShardRegion.MessageExtractor {

  override fun entityId(message: Any): String? {
    return if (message is ToEntityEnvelope) {
      message.entityId.toString()
    } else {
      null
    }
  }

  override fun entityMessage(message: Any): Any {
    // It IS the payload itself. No need to extract anything.
    return message
  }

  override fun shardId(message: Any): String? {
    return if (message is ToEntityEnvelope) {
      val id = message.entityId
      (id % NUMBER_OF_SHARDS).toString()
    } else {
      null
    }
  }

  companion object {
    private const val NUMBER_OF_SHARDS = 10
  }
}