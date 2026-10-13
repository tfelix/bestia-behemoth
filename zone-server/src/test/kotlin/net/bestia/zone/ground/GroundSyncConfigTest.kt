package net.bestia.zone.ground

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroundSyncConfigTest {

  /** A real minute at the shipped speed factor of 3, which is what the yml comment promises. */
  @Test
  fun `the default is a minute of real time`() {
    assertEquals(180L, GroundSyncConfig().resyncIntervalSeconds)
  }

  @Test
  fun `zero means never resync`() {
    assertFalse(GroundSyncConfig(resyncIntervalSeconds = 0).resyncs)
    assertTrue(GroundSyncConfig(resyncIntervalSeconds = 1).resyncs)
  }

  @Test
  fun `a negative interval is refused`() {
    assertFailsWith<IllegalArgumentException> { GroundSyncConfig(resyncIntervalSeconds = -1) }
  }
}
