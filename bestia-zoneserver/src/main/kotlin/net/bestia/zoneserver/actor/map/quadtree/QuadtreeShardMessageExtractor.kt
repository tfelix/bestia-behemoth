package net.bestia.zoneserver.actor.map.quadtree

import akka.cluster.sharding.ShardRegion

class QuadtreeShardMessageExtractor(
    private val numberOfShards: Int = 10
) : ShardRegion.MessageExtractor {

  override fun entityId(message: Any): String? {
    return when (message) {
      is QueryCommand -> message.nodeAdress
      else -> null
    }
  }

  override fun entityMessage(message: Any): Any? {
    return message
  }

  override fun shardId(message: Any): String? {
    return when (message) {
      is QueryCommand -> (message.nodeAdress.hashCode() % numberOfShards).toString()
      else -> null
    }
  }
}