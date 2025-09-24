package net.bestia.zone.ecs

import com.github.quillraven.fleks.Entity
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ZoneConfig
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Fleks uses a strange entity system so we internally map their entity refs towards a single use
 * entity ID. The EntityId is a globally unique ID of that entity.
 */
@Service
@ZoneInjectable
class EntityRegistry(
  zoneConfig: ZoneConfig
) {

  private class IdGenerator(
    private val nodeId: Int,
    private val epochMillis: Long = 1704067200000L // 2024-01-01 as custom epoch
  ) {
    init {
      require(nodeId in 0..255) { "Node ID must be between 0 and 255" }
    }

    // Bit allocation
    private val nodeBits = 8
    private val sequenceBits = 11

    private val maxSequence = (1 shl sequenceBits) - 1 // 2047
    private val nodeShift = sequenceBits
    private val timestampShift = sequenceBits + nodeBits

    @Volatile
    private var lastTimestamp = -1L
    @Volatile
    private var sequence = 0

    @Synchronized
    fun nextId(): Long {
      val currentTimestamp = System.currentTimeMillis()

      if (currentTimestamp < lastTimestamp) {
        throw IllegalStateException("Clock moved backwards. Refusing to generate id")
      }

      if (currentTimestamp == lastTimestamp) {
        sequence++
        if (sequence > maxSequence) {
          // Sequence overflow → keine weiteren IDs in diesem ms
          throw IllegalStateException("Too many IDs generated in the same millisecond")
        }
      } else {
        sequence = 0
        lastTimestamp = currentTimestamp
      }

      val timestampPart = (currentTimestamp - epochMillis) shl timestampShift
      val nodePart = (nodeId and 0xFF) shl nodeShift
      val seqPart = sequence and maxSequence

      return timestampPart or nodePart.toLong() or seqPart.toLong()
    }
  }

  private val idGen = IdGenerator(zoneConfig.shardId)

  private val entityIdsToRef = ConcurrentHashMap<EntityId, Entity>()
  private val refToEntityId = ConcurrentHashMap<Entity, EntityId>()

  fun getEntity(id: EntityId): Entity? {
    return entityIdsToRef[id]
  }

  fun getEntityId(entity: Entity): EntityId? {
    return refToEntityId[entity]
  }

  fun getEntityIdOrThrow(entity: Entity): EntityId {
    return getEntityId(entity)
      ?: throw IllegalStateException("Global entity ID not found for entity: $entity")
  }

  @Synchronized
  fun registerEntity(entity: Entity): EntityId {
    val newId = idGen.nextId()
    entityIdsToRef[newId] = entity
    refToEntityId[entity] = newId

    LOG.trace { "Mapping entity $entity to id $newId" }

    return newId
  }

  @Synchronized
  fun deleteEntity(entityId: EntityId): EntityId? {
    val entity = entityIdsToRef.remove(entityId)
    if (entity != null) {
      refToEntityId.remove(entity)

      return entityId
    } else {
      return null
    }
  }

  @Synchronized
  fun deleteEntity(entity: Entity): EntityId? {
    val entityId = refToEntityId.remove(entity)
    if (entityId != null) {
      entityIdsToRef.remove(entityId)
    }

    return entityId
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}