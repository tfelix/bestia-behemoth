package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId

/**
 * Snowflake-style [EntityId] generator (timestamp | node | sequence). Produces ids in the same
 * space the project used before (via the old `net.bestia.zone.ecs.EntityIdGenerator`) so entity ids
 * stay unique and roughly monotonic across the zone.
 */
class SnowflakeEntityIdGenerator(
  private val nodeId: Int = 1,
  private val epochMillis: Long = 1704067200000L, // 2024-01-01 as custom epoch
): EntityIdGenerator {
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
  override fun nextId(): EntityId {
    var currentTimestamp = java.lang.System.currentTimeMillis()

    if (currentTimestamp < lastTimestamp) {
      throw IllegalStateException("Clock moved backwards. Refusing to generate id")
    }

    if (currentTimestamp == lastTimestamp) {
      sequence++
      if (sequence > maxSequence) {
        currentTimestamp = waitForNextMillis(lastTimestamp)
        sequence = 0
        lastTimestamp = currentTimestamp
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

  /**
   * Blocks until the clock leaves [previous] behind, so the sequence can start over.
   *
   * This used to throw instead, and the exception was worse than the wait in every way that matters.
   * 2048 ids in one millisecond is not an abuse case: one generator serves the whole zone, and
   * materialising a batch of entities - a chunk's worth of world objects, a spawner topping up a
   * pack, a player's whole view volume after a teleport - asks for hundreds at a time on a single
   * tick. Failing that means the caller is a `nextId()` deep inside a loop, and the throw surfaces
   * in `ZoneEngine`'s per-tick catch-all, which silently abandons the rest of the tick. So a
   * legitimate burst became missing entities somewhere unrelated.
   *
   * The wait is bounded by construction: at worst it is the remainder of the current millisecond,
   * once per 2048 ids. A tick is fifty milliseconds.
   */
  private fun waitForNextMillis(previous: Long): Long {
    var now = java.lang.System.currentTimeMillis()

    while (now <= previous) {
      Thread.onSpinWait()
      now = java.lang.System.currentTimeMillis()
    }

    return now
  }
}
