package net.bestia.zone.ecs.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class Long2IntOpenHashMapTest {

  @Test
  fun `put get and containsKey work across a resize`() {
    val map = Long2IntOpenHashMap(initialCapacity = 2)
    for (i in 0 until 1000) {
      // use snowflake-shaped (large, sparse) keys
      map.put(1_700_000_000_000L + i * 7L, i)
    }
    assertEquals(1000, map.size)
    for (i in 0 until 1000) {
      val key = 1_700_000_000_000L + i * 7L
      assertTrue(map.containsKey(key))
      assertEquals(i, map.get(key))
    }
    assertEquals(Long2IntOpenHashMap.ABSENT, map.get(-999L))
    assertFalse(map.containsKey(-999L))
  }

  @Test
  fun `put returns previous value and overwrites`() {
    val map = Long2IntOpenHashMap()
    assertEquals(Long2IntOpenHashMap.ABSENT, map.put(42L, 1))
    assertEquals(1, map.put(42L, 2))
    assertEquals(2, map.get(42L))
    assertEquals(1, map.size)
  }

  @Test
  fun `remove with backward-shift keeps other keys reachable`() {
    val map = Long2IntOpenHashMap(initialCapacity = 64)
    val keys = (0 until 500).map { Random.nextLong() }.distinct()
    keys.forEachIndexed { idx, k -> map.put(k, idx) }

    // Remove half, then assert the remaining half is still intact.
    val toRemove = keys.filterIndexed { i, _ -> i % 2 == 0 }
    toRemove.forEach { map.remove(it) }

    keys.forEachIndexed { idx, k ->
      if (idx % 2 == 0) {
        assertFalse(map.containsKey(k), "expected $k removed")
      } else {
        assertTrue(map.containsKey(k), "expected $k present")
        assertEquals(idx, map.get(k))
      }
    }
    assertEquals(keys.size - toRemove.size, map.size)
  }
}
