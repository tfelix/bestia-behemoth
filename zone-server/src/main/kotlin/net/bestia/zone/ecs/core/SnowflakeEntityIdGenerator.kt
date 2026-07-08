package net.bestia.zone.ecs.core

/**
 * Snowflake-style [EntityId] generator (timestamp | node | sequence). Produces ids in the same
 * space the project used before (via the old `net.bestia.zone.ecs.EntityIdGenerator`) so entity ids
 * stay unique and roughly monotonic across the zone.
 */
class SnowflakeEntityIdGenerator(
  private val nodeId: Int = 1,
  private val epochMillis: Long = 1704067200000L, // 2024-01-01 as custom epoch
) {
  init {
    require(nodeId in 0..255) { "Node ID must be between 0 and 255" }
  }

  private val sequenceBits = 11
  private val nodeBits = 8
  private val maxSequence = (1 shl sequenceBits) - 1 // 2047
  private val nodeShift = sequenceBits
  private val timestampShift = sequenceBits + nodeBits

  private var lastTimestamp = -1L
  private var sequence = 0

  @Synchronized
  fun nextId(): EntityId {
    val currentTimestamp = System.currentTimeMillis()

    if (currentTimestamp < lastTimestamp) {
      throw IllegalStateException("Clock moved backwards. Refusing to generate id")
    }

    if (currentTimestamp == lastTimestamp) {
      sequence++
      if (sequence > maxSequence) {
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
