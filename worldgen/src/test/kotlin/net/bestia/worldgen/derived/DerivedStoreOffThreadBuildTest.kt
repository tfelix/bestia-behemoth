package net.bestia.worldgen.derived

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.VoxelChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The begin / build / finish route a worker thread takes, against the synchronous [DerivedStore.rebuild]. */
class DerivedStoreOffThreadBuildTest {

  private val chunk = ChunkPos(0, 0, 0)
  private val size = 8
  private val height = 24

  private fun ground(at: ChunkPos): VoxelChunk {
    val voxels = VoxelChunk(at, size, height)
    for (y in 0 until size) {
      for (x in 0 until size) {
        for (z in 0..3) voxels[x, y, z] = BlockType.GRANITE
      }
    }
    return voxels
  }

  private fun store() = DerivedStore(voxels = ::ground)

  @Test
  fun `a chunk being built elsewhere is stale until its tile comes back`() {
    val store = store()
    store.track(chunk)

    assertEquals(chunk, store.beginBuild())

    assertTrue(store.isStale(chunk), "its structures are not in the store yet")
    assertFalse(store.isTracked(chunk))
    assertEquals(1, store.pendingRebuilds)
    assertNull(store.beginBuild(), "it left the queue, so nobody else builds it")

    assertTrue(store.finishBuild(chunk, store.buildTile(chunk, ::ground)))

    assertFalse(store.isStale(chunk))
    assertTrue(store.isTracked(chunk))
    assertNotNull(store.walkableOf(chunk))
  }

  @Test
  fun `a chunk edited while it was being built is queued again and the old tile dropped`() {
    val store = store()
    store.track(chunk)
    store.beginBuild()
    val tile = store.buildTile(chunk, ::ground)

    store.invalidate(chunk)

    assertFalse(store.finishBuild(chunk, tile), "the tile describes voxels that have changed since")
    assertFalse(store.isTracked(chunk))
    assertEquals(chunk, store.beginBuild(), "it is back on the queue for a build from what is there now")
  }

  @Test
  fun `a chunk forgotten while it was being built takes no tile`() {
    val store = store()
    store.track(chunk)
    store.beginBuild()
    val tile = store.buildTile(chunk, ::ground)

    store.forget(chunk)

    assertFalse(store.finishBuild(chunk, tile))
    assertFalse(store.isTracked(chunk))
    assertFalse(store.isStale(chunk))
  }

  @Test
  fun `a build that failed leaves the chunk queued`() {
    val store = store()
    store.track(chunk)
    store.beginBuild()

    store.abandonBuild(chunk)

    assertEquals(1, store.pendingRebuilds)
    assertEquals(chunk, store.beginBuild())
  }
}
