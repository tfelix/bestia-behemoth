package net.bestia.zone.shard

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ZoneConfig
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps track which shard is responsible for which entity. Later this will be implemented
 * via e.g. etcd to keep track on which zone a certain entity lives.
 */
@Component
class EntityShardRegistry(
  private val zoneConfig: ZoneConfig
) {
  // entityId -> shardId
  private val ownership = ConcurrentHashMap<EntityId, Int>()

  fun setOwnerToCurrentShard(entityId: EntityId) {
    LOG.trace { "Set entity: $entityId to shard owner: ${zoneConfig.shardId}" }
    ownership[entityId] = zoneConfig.shardId
  }

  fun getOwnerShard(entityId: EntityId): Int? {
    return ownership[entityId]
  }

  fun remove(entityId: EntityId) {
    LOG.trace {
      val currentOwner = getOwnerShard(entityId)
      "Removing entity: $entityId previous shard owner: $currentOwner"
    }
    ownership.remove(entityId)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

