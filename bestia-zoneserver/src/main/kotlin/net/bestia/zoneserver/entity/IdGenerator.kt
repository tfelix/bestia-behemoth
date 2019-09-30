package net.bestia.zoneserver.entity

import net.bestia.zoneserver.config.ZoneserverNodeConfig
import org.springframework.stereotype.Component
import java.time.Instant
import kotlin.math.pow

/**
 * The ID generator is inspired by Twitters Snowflake Generator
 * (https://github.com/twitter/snowflake/tree/snowflake-2010) and adapted from
 * https://www.callicoder.com/distributed-unique-id-sequence-number-generator/
 * for Kotlin.
 *
 * This class should be used as a Singleton.
 * Make sure that you create and reuse a Single instance of SequenceGenerator per node in your distributed system cluster.
 */
@Component
class IdGenerator(
    config: ZoneserverNodeConfig
) {

  private val nodeId = config.nodeId

  @Volatile
  private var lastTimestamp = -1L
  @Volatile
  private var sequence = 0L

  init {
    require(!(nodeId < 0 || nodeId > maxNodeId)) { "NodeId must be between 0 and $maxNodeId" }
  }

  // Block and wait till next millisecond
  private fun waitNextMillis(currentTimestamp: Long): Long {
    var currentTimestamp = currentTimestamp
    while (currentTimestamp == lastTimestamp) {
      currentTimestamp = timestamp()
    }
    return currentTimestamp
  }

  @Synchronized
  fun newId(): Long {
    var currentTimestamp = timestamp()

    check(currentTimestamp >= lastTimestamp) { "Invalid System Clock!" }

    if (currentTimestamp == lastTimestamp) {
      sequence = sequence + 1 and maxSequence
      if (sequence == 0L) {
        // Sequence Exhausted, wait till next millisecond.
        currentTimestamp = waitNextMillis(currentTimestamp)
      }
    } else {
      // reset sequence to start with zero for the next millisecond
      sequence = 0
    }

    lastTimestamp = currentTimestamp

    var id = currentTimestamp shl TOTAL_BITS - EPOCH_BITS
    id = id or (nodeId shl TOTAL_BITS - EPOCH_BITS - NODE_ID_BITS).toLong()
    id = id or sequence

    return id
  }

  // Get current timestamp in milliseconds, adjust for the custom epoch.
  private fun timestamp(): Long {
    return Instant.now().toEpochMilli() - CUSTOM_EPOCH
  }

  companion object {
    private const val TOTAL_BITS = 64
    private const val EPOCH_BITS = 42
    private const val NODE_ID_BITS = 10
    private const val SEQUENCE_BITS = 12

    private val maxNodeId = (2.0.pow(NODE_ID_BITS) - 1).toInt()
    private val maxSequence = (2.0.pow(SEQUENCE_BITS) - 1).toLong()

    // Custom Epoch (January 1, 2015 Midnight UTC = 2015-01-01T00:00:00Z)
    private const val CUSTOM_EPOCH = 1420070400000L
  }
}