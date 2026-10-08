package net.bestia.zone.world.stream

import net.bestia.zone.world.stream.ChunkStreamSystem.Companion.BLIND_PATCH_LIMIT_BYTES
import net.bestia.zone.world.stream.ChunkStreamSystem.Companion.prefersSnapshot
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PatchOrSnapshotTest {

  @Test
  fun `a patch smaller than the cached chunk is sent as a patch`() {
    assertFalse(prefersSnapshot(patchBytes = 200, snapshotBytes = 3_000))
  }

  @Test
  fun `a patch as large as the cached chunk loses to it`() {
    assertTrue(prefersSnapshot(patchBytes = 3_000, snapshotBytes = 3_000))
  }

  @Test
  fun `without a cached size only a large patch loses`() {
    assertFalse(prefersSnapshot(patchBytes = 200, snapshotBytes = null))
    assertTrue(prefersSnapshot(patchBytes = BLIND_PATCH_LIMIT_BYTES + 1, snapshotBytes = null))
  }
}
