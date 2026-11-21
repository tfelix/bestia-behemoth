package net.bestia.zone.stronghold

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackProgressTest {

  @Test
  fun `five steps make a level, and a step carries over`() {
    assertEquals(TrackProgress(0, 4), TrackProgress().advanced(4))
    assertEquals(TrackProgress(1, 0), TrackProgress(0, 4).advanced(1))
    assertEquals(TrackProgress(1, 1), TrackProgress(0, 4).advanced(2))
  }

  @Test
  fun `nothing is kept past level ten`() {
    val top = TrackProgress(9, 4).advanced(2)

    assertEquals(TrackProgress(10, 0), top)
    assertTrue(top.isMaxed)
  }

  @Test
  fun `a capture costs one level and keeps the steps`() {
    assertEquals(TrackProgress(2, 3), TrackProgress(3, 3).lostLevel())
    assertEquals(TrackProgress(0, 3), TrackProgress(0, 3).lostLevel())
  }
}
