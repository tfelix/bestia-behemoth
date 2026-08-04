package net.bestia.zone.ecs.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SnowflakeEntityIdGeneratorTest {

  /**
   * The burst case, and the reason this test exists: the sequence is eleven bits, so 2048 ids
   * exhaust one millisecond. Materialising a chunk's world objects or a player's whole view volume
   * after a teleport asks for that many on a single tick, and the generator used to throw - which
   * surfaced in `ZoneEngine`'s per-tick catch-all and silently abandoned the rest of the tick.
   */
  @Test
  fun `a burst well past one millisecond's worth of sequence yields unique ids`() {
    val generator = SnowflakeEntityIdGenerator(nodeId = 7)
    val ids = HashSet<Long>()

    repeat(5_000) { ids.add(generator.nextId()) }

    assertEquals(5_000, ids.size, "every id in the burst must be distinct")
  }

  @Test
  fun `ids stay strictly increasing across the millisecond boundaries a burst crosses`() {
    val generator = SnowflakeEntityIdGenerator(nodeId = 7)
    var previous = Long.MIN_VALUE

    repeat(5_000) {
      val id = generator.nextId()
      assertTrue(id > previous, "id $id did not exceed its predecessor $previous")
      previous = id
    }
  }

  /**
   * The node id has to survive the sequence rolling over, because the rollover path rewrites the
   * timestamp and the sequence and could plausibly drop it.
   */
  @Test
  fun `the node id survives a sequence rollover`() {
    val nodeId = 200
    val generator = SnowflakeEntityIdGenerator(nodeId = nodeId)

    repeat(5_000) {
      val id = generator.nextId()
      assertEquals(nodeId.toLong(), (id shr 11) and 0xFF, "node id lost from id $id")
    }
  }
}
