package net.bestia.zone.ecs

class EntityIdGenerator(
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