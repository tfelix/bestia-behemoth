package net.bestia.zone.world.stream

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.derived.DerivedStore
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.VoxelChunk
import net.bestia.zone.geometry.Vec3L
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChunkWalkQueryCornerTest {

  private val world = WorldConfig(seed = 1L)

  /** Flat sand stood on at 1, and a pillar of rock at (5, 4) too tall to step onto. */
  private fun query(): ChunkWalkQuery {
    val slab = ChunkPos(0, 0, 0)
    val ground = VoxelChunk(slab, world.chunkSize, world.chunkHeight)
    for (y in 0 until world.chunkSize) {
      for (x in 0 until world.chunkSize) ground[x, y, 0] = BlockType.SAND
    }
    for (z in 1..4) ground[5, 4, z] = BlockType.STONE

    val derived = DerivedStore(voxels = { if (it == slab) ground else VoxelChunk(it, world.chunkSize, world.chunkHeight) })
    derived.track(slab)
    derived.rebuildAll()

    val service = mockk<ChunkService> {
      every { isReady } returns true
      every { config } returns world
      every { derived() } returns derived
      every { normalise(any()) } answers { firstArg() }
    }

    return ChunkWalkQuery(service)
  }

  @Test
  fun `a diagonal past the corner of a pillar is refused`() {
    assertFalse(query().canStep(Vec3L(4, 4, 1), Vec3L(5, 5, 1)))
    assertFalse(query().canStep(Vec3L(5, 5, 1), Vec3L(4, 4, 1)), "from the other side too")
  }

  @Test
  fun `a diagonal with open ground on both sides is taken`() {
    assertTrue(query().canStep(Vec3L(4, 4, 1), Vec3L(3, 5, 1)))
  }

  @Test
  fun `a straight step beside the pillar is taken`() {
    assertTrue(query().canStep(Vec3L(4, 4, 1), Vec3L(4, 5, 1)))
  }
}
