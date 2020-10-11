package net.bestia.zoneserver.actor.entity

import akka.cluster.sharding.ShardRegion
import net.bestia.messages.entity.EntityMessage

/**
 * Defines methods for extracting the shard id from the incoming messages for
 * entity actors.
 *
 * @author Thomas Felix
 */
class EntityShardMessageExtractor : ShardRegion.MessageExtractor {

  override fun entityId(message: Any): String? {
    return when(message) {
      is EntityMessage -> message.entityId.toString()
      else -> null
    }
  }

  /**
   * Message is the payload no need to extract it.
   */
  override fun entityMessage(message: Any): Any? {
    return when(message) {
      is EntityMessage -> message
      else -> null
    }
  }

  override fun shardId(message: Any): String? {
    return when(message) {
      is EntityMessage -> (message.entityId % NUMBER_OF_SHARDS).toString()
      else -> null
    }
  }

  companion object {
    private const val NUMBER_OF_SHARDS = 10
  }
}