package net.bestia.zone.structure

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.derived.Edges
import net.bestia.worldgen.voxel.Occupancy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StructureCellIndexTest {

  private val world = WorldConfig(seed = 1L)
  private val size = world.chunkSize.toLong()

  private fun index(): StructureCellIndex {
    return StructureCellIndex(world) { it }
  }

  private fun block(x: Long, y: Long, z: Long): StructureCell {
    return StructureCell(x, y, z, Occupancy.FULL, Edges.NONE)
  }

  @Test
  fun `a piece shows in the chunk it stands in, and is gone when removed`() {
    val index = index()

    assertEquals(setOf(ChunkPos(0, 0, 0)), index.put(1, listOf(block(3, 4, 5))))
    assertEquals(Occupancy.FULL, index.cellsOf(ChunkPos(0, 0, 0))!!.fillAt(3, 4, 5))

    assertEquals(setOf(ChunkPos(0, 0, 0)), index.remove(1))
    assertNull(index.cellsOf(ChunkPos(0, 0, 0)))
  }

  @Test
  fun `a piece across a chunk border is split between both chunks`() {
    val index = index()

    val changed = index.put(1, listOf(block(size - 1, 0, 5), block(size, 0, 5)))

    assertEquals(setOf(ChunkPos(0, 0, 0), ChunkPos(1, 0, 0)), changed)
    assertEquals(Occupancy.FULL, index.cellsOf(ChunkPos(0, 0, 0))!!.fillAt(world.chunkSize - 1, 0, 5))
    assertEquals(Occupancy.FULL, index.cellsOf(ChunkPos(1, 0, 0))!!.fillAt(0, 0, 5))
  }

  @Test
  fun `a negative position lands in the chunk below zero`() {
    val index = index()

    assertEquals(setOf(ChunkPos(-1, 0, -1)), index.put(1, listOf(block(-1, 0, -1))))
  }

  @Test
  fun `two pieces in one chunk both show, and removing one keeps the other`() {
    val index = index()
    index.put(1, listOf(block(1, 1, 1)))
    index.put(2, listOf(block(2, 2, 2)))

    index.remove(1)

    val cells = index.cellsOf(ChunkPos(0, 0, 0))!!
    assertEquals(Occupancy.EMPTY, cells.fillAt(1, 1, 1))
    assertEquals(Occupancy.FULL, cells.fillAt(2, 2, 2))
  }

  @Test
  fun `a filled cell or a wall already standing is taken`() {
    val index = index()
    index.put(1, listOf(block(1, 1, 1), StructureCell(5, 5, 1, Occupancy.EMPTY, Edges.X_PLUS)))

    assertTrue(index.isTaken(listOf(block(1, 1, 1))))
    assertTrue(index.isTaken(listOf(StructureCell(5, 5, 1, Occupancy.EMPTY, Edges.X_PLUS))))
    assertFalse(index.isTaken(listOf(StructureCell(5, 5, 1, Occupancy.EMPTY, Edges.Y_PLUS))), "another side")
    assertFalse(index.isTaken(listOf(StructureCell(1, 1, 1, Occupancy.EMPTY, Edges.X_PLUS))), "a wall on a block")
    assertFalse(index.isTaken(listOf(block(9, 9, 9))))
  }
}
