package net.bestia.zone.prop.station

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StationContentsTest {

  @Test
  fun `a queue and its held items come back as they were written`() {
    val queue = listOf(QueueEntry(1L, 3, done = 1), QueueEntry(2L, 1))
    val held = mapOf(50L to 4, 51L to 1)

    assertEquals(queue, StationContents.decodeQueue(StationContents.encodeQueue(queue)))
    assertEquals(held, StationContents.decodeHeld(StationContents.encodeHeld(held)))
  }

  @Test
  fun `an empty station writes nothing and reads back empty`() {
    assertNull(StationContents.encodeQueue(emptyList()))
    assertNull(StationContents.encodeHeld(mapOf(50L to 0)))
    assertTrue(StationContents.decodeQueue(null).isEmpty())
    assertTrue(StationContents.decodeHeld(null).isEmpty())
  }
}
