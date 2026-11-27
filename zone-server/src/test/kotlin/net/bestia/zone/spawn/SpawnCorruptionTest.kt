package net.bestia.zone.spawn

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpawnCorruptionTest {

  @Test
  fun `rotten land breeds corrupted bestia, and a world without the Withering never does`() {
    val field = SpawnCorruption { x, _ -> if (x < 100.0) 0.6 else 0.3 }

    assertTrue(isCorrupted(field, 10, 0))
    assertFalse(isCorrupted(field, 500, 0))
    assertFalse(isCorrupted(null, 10, 0))
  }
}
