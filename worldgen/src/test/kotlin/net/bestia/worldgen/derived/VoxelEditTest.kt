package net.bestia.worldgen.derived

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.VoxelChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VoxelEditTest {

  @Test
  fun `an edit reads back what was packed`() {
    val edit = VoxelEdit.pack(262_143, BlockType.WATER, 77)

    assertEquals(262_143, VoxelEdit.indexOf(edit))
    assertEquals(BlockType.WATER, VoxelEdit.blockOf(edit))
    assertEquals(77, VoxelEdit.occupancyOf(edit))
  }

  @Test
  fun `edits sort by voxel index whatever they hold`() {
    val low = VoxelEdit.pack(10, BlockType.GEM_DIAMOND_RICH, Occupancy.FULL)
    val high = VoxelEdit.pack(11, BlockType.AIR, Occupancy.EMPTY)

    assertTrue(low < high)
  }

  @Test
  fun `air with material and material without any are refused`() {
    assertFailsWith<IllegalArgumentException> { VoxelEdit.pack(0, BlockType.AIR, 1) }
    assertFailsWith<IllegalArgumentException> { VoxelEdit.pack(0, BlockType.WATER, Occupancy.EMPTY) }
  }

  @Test
  fun `an edit can be read off a chunk`() {
    val voxels = VoxelChunk(ChunkPos(0, 0), 4, 8)
    voxels.set(1, 2, 3, BlockType.GRANITE, 96)

    val edit = VoxelEdit.of(voxels, voxels.index(1, 2, 3))

    assertEquals(VoxelEdit.pack(voxels.index(1, 2, 3), BlockType.GRANITE, 96), edit)
  }
}
