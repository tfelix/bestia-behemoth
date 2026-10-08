package net.bestia.zone.water.sim

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.derived.VoxelEdit
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.VoxelChunk
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaterChunkTest {

  private val pos = ChunkPos(0, 0, 0)

  @Test
  fun `generated water is a source, placed water is open, and everything solid is a wall`() {
    val base = VoxelChunk(pos, 2, 4)
    base.set(0, 0, 0, BlockType.WATER, 200)
    base.set(1, 0, 0, BlockType.ICE, Occupancy.FULL)
    base.set(0, 1, 0, BlockType.LAVA, Occupancy.FULL)
    val merged = base.copy()
    merged.set(1, 1, 0, BlockType.WATER, 50)

    val chunk = WaterChunk.of(merged, base)

    assertTrue(chunk.isSource(base.index(0, 0, 0)))
    assertEquals(200, chunk.fillAt(base.index(0, 0, 0)))
    assertTrue(chunk.isWall(base.index(1, 0, 0)), "ice")
    assertTrue(chunk.isWall(base.index(0, 1, 0)), "lava")
    assertFalse(chunk.isWall(base.index(1, 1, 0)))
    assertFalse(chunk.isSource(base.index(1, 1, 0)))
    assertEquals(50, chunk.fillAt(base.index(1, 1, 0)))
    assertFalse(chunk.isWall(base.index(1, 1, 1)), "air holds water")
  }

  @Test
  fun `edits are the cells that changed since the last commit`() {
    val chunk = WaterChunk(pos, 2, 4)
    chunk.loadFill(3, 100)

    chunk.setFill(5, 40)
    chunk.setFill(3, 0)
    chunk.setFill(6, 9)
    chunk.setFill(6, 0)

    assertContentEquals(
      longArrayOf(
        VoxelEdit.pack(3, BlockType.AIR, Occupancy.EMPTY),
        VoxelEdit.pack(5, BlockType.WATER, 40)
      ),
      chunk.takeEdits()
    )
    assertEquals(0, chunk.takeEdits().size, "committed edits are not handed out twice")
  }

  @Test
  fun `only a flip or a large move is worth committing`() {
    val chunk = WaterChunk(pos, 2, 4)
    chunk.loadFill(0, 100)

    chunk.setFill(0, 120)
    assertFalse(chunk.hasEditsWorthCommitting)

    chunk.setFill(0, 100 + WaterChunk.COMMIT_STEP)
    assertTrue(chunk.hasEditsWorthCommitting)

    chunk.takeEdits()
    chunk.setFill(1, 1)
    assertTrue(chunk.hasEditsWorthCommitting, "air turning to water is worth committing however little")
  }

  @Test
  fun `crossing a face and back returns to the same cell`() {
    val chunk = WaterChunk(pos, 3, 5)
    val back = mapOf(
      Face.DOWN to Face.UP, Face.UP to Face.DOWN, Face.WEST to Face.EAST,
      Face.EAST to Face.WEST, Face.SOUTH to Face.NORTH, Face.NORTH to Face.SOUTH
    )

    for (index in 0 until chunk.volume) {
      for (face in Face.entries) {
        val there = chunk.neighbourIndex(index, face)
        assertEquals(index, chunk.neighbourIndex(there, back.getValue(face)), "cell $index across $face")
      }
    }
  }
}
