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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sea level is where slab -1 meets slab 0, so the ground along a coast straddles a slab boundary: a beach a voxel
 * up in slab 0, and dry lowland whose top voxel is the last one of slab -1. Reported as a player bestia that walked
 * to the last tile of a beach and was refused every step after it, while the client drew dirt ahead of it.
 *
 * Two things were wrong. The lowland had no walkable surface at all, because its headroom is in the slab above;
 * and a step was looked for in the origin's slab only, so even a surface there could not be stepped onto.
 *
 * Hand-built rather than generated, unlike [ChunkWalkQueryTest]: what this needs is two particular columns either
 * side of a boundary, and a generated coast promises neither.
 */
class ChunkWalkQuerySlabBoundaryTest {

  private val world = WorldConfig(seed = 1L)

  /** Sand in slab 0's first voxel, so it is stood on at 1. */
  private val beach = Vec3L(1, 0, 1)

  /** Rock to the top of slab -1 and nothing in slab 0, so it is stood on at 0. */
  private val lowland = Vec3L(0, 0, 0)

  /** Three voxels of sand: two voxels up from the beach, which is a wall at the default slope. */
  private val dune = Vec3L(2, 0, 3)

  private fun query(): ChunkWalkQuery {
    val size = world.chunkSize
    val height = world.chunkHeight
    val surfaceSlab = ChunkPos(0, 0, 0)
    val slabBelow = ChunkPos(0, 0, -1)

    val upper = VoxelChunk(surfaceSlab, size, height)
    upper[1, 0, 0] = BlockType.SAND
    for (z in 0..2) upper[2, 0, z] = BlockType.SAND

    val lower = VoxelChunk(slabBelow, size, height)
    for (y in 0 until size) {
      for (x in 0 until size) {
        for (z in 0 until height) lower[x, y, z] = BlockType.STONE
      }
    }

    val chunks = mapOf(surfaceSlab to upper, slabBelow to lower)
    val derived = DerivedStore(voxels = { chunks[it] ?: VoxelChunk(it, size, height) })
    derived.track(surfaceSlab)
    derived.track(slabBelow)
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
  fun `lowland just under sea level has ground to stand on`() {
    assertEquals(0L, query().surfaceAt(lowland))
  }

  @Test
  fun `a beach steps down onto the lowland under sea level, and back up`() {
    val query = query()

    assertTrue(query.canStep(beach, lowland), "one voxel down, across the slab boundary")
    assertTrue(query.canStep(lowland, beach), "one voxel up, across it the other way")
  }

  @Test
  fun `the lowland is walkable across itself`() {
    assertTrue(query().canStep(lowland, Vec3L(0, 1, 0)))
  }

  @Test
  fun `a rise too steep is still refused`() {
    assertFalse(query().canStep(beach, dune), "looking across a slab boundary opens a step, not a wall")
  }
}
