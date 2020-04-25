package net.bestia.zoneserver.actor.entity

import akka.cluster.sharding.ShardRegion

/**
 * Sends messages to a octree node for collision detection.
 */
class OctreeShardMessageExtractor : ShardRegion.MessageExtractor {

  override fun entityId(message: Any): String? {
    return when (message) {
      is OctreeEnvelope -> message.identifer
      else -> null
    }
  }

  /**
   * Message is the payload no need to extract it.
   */
  override fun entityMessage(message: Any): Any? {
    return message
  }

  override fun shardId(message: Any): String? {
    return when (message) {
      is OctreeEnvelope -> (message.identifer.hashCode() % NUMBER_OF_SHARDS).toString()
      else -> null
    }
  }

  companion object {
    private const val NUMBER_OF_SHARDS = 10
  }
}