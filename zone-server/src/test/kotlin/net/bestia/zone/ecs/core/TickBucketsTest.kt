package net.bestia.zone.ecs.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TickBucketsTest {

  @Test
  fun `an id is due exactly once in every period`() {
    val period = 20L

    for (start in listOf(0L, 7L, 1_000L)) {
      val due = (start until start + period).count { TickBuckets.isDue(it, ID, period) }
      assertEquals(1, due, "window from $start")
    }
  }

  @Test
  fun `the next due tick is strictly later and is due`() {
    val period = 20L

    for (tick in 0L until 50L) {
      val next = TickBuckets.nextDue(tick, ID, period)

      assertTrue(next in tick + 1..tick + period, "from $tick got $next")
      assertTrue(TickBuckets.isDue(next, ID, period))
      assertTrue((tick + 1 until next).none { TickBuckets.isDue(it, ID, period) }, "skipped a due tick after $tick")
    }
  }

  @Test
  fun `a period of one is every tick`() {
    assertTrue((0L until 10L).all { TickBuckets.isDue(it, ID, 1) })
    assertEquals(6L, TickBuckets.nextDue(5L, ID, 1))
  }

  /** Snowflake ids minted together differ only above their low bits, which `id % period` would ignore. */
  @Test
  fun `ids minted together spread over the period`() {
    val period = 20L
    val ids = (0L until 200L).map { (SNOWFLAKE_TIME shl 22) or (it shl 12) }

    val buckets = ids.map { id -> (0L until period).first { TickBuckets.isDue(it, id, period) } }.toSet()

    assertTrue(buckets.size >= period - 2, "only ${buckets.size} of $period ticks used")
  }

  private companion object {
    const val ID = 4_242L
    const val SNOWFLAKE_TIME = 123_456_789L
  }
}
