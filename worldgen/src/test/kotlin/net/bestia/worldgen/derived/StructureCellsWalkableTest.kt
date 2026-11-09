package net.bestia.worldgen.derived

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.VoxelChunk
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StructureCellsWalkableTest {

  private val pos = ChunkPos(0, 0, 0)
  private val size = 8
  private val height = 24

  /** The running game's terrain rule: a step may rise tan 60° per voxel. */
  private val agent = AgentProfile.forMaxSlope(voxelSize = 1.0, degrees = 60.0)

  /** Ground whose surface is 4.0 everywhere. */
  private fun flatGround(): VoxelChunk {
    val chunk = VoxelChunk(pos, size, height)
    for (y in 0 until size) {
      for (x in 0 until size) {
        for (z in 0..3) chunk[x, y, z] = BlockType.GRANITE
      }
    }
    return chunk
  }

  private fun cells(build: StructureCells.Builder.() -> Unit): StructureCells {
    return StructureCells.Builder(size).apply(build).build()
  }

  private fun StructureCells.Builder.block(x: Int, y: Int, z: Int, fraction: Double = 1.0) {
    add(x, y, z, Occupancy.of(fraction), Edges.NONE)
  }

  /** A wall on the border between (x, y) and (x + 1, y), written on both sides as the rasterizer does. */
  private fun StructureCells.Builder.wallTowardsXPlus(x: Int, y: Int, z: Int) {
    add(x, y, z, Occupancy.EMPTY, Edges.X_PLUS)
    add(x + 1, y, z, Occupancy.EMPTY, Edges.X_MINUS)
  }

  @Test
  fun `a floor over the ground gives the column a second, built surface`() {
    val tile = WalkableTile.of(flatGround(), agent, cells { block(2, 2, 7) })

    assertContentEquals(doubleArrayOf(4.0, 8.0), tile.surfacesAt(2, 2))
    assertFalse(tile.isStructure(tile.spanAt(2, 2, 3)), "the ground under the floor is still terrain")
    assertTrue(tile.isStructure(tile.spanAt(2, 2, 7)))
  }

  @Test
  fun `a built cell in the headroom takes the floor under it away`() {
    val tile = WalkableTile.of(flatGround(), agent, cells { block(2, 2, 5) })

    assertContentEquals(doubleArrayOf(6.0), tile.surfacesAt(2, 2), "only the top of the block is left to stand on")
  }

  @Test
  fun `a two metre block is a wall`() {
    val tile = WalkableTile.of(
      flatGround(), agent,
      cells {
        block(3, 0, 4)
        block(3, 0, 5)
      }
    )

    assertEquals(-1.0, tile.stepTarget(3, 0, fromSurface = 4.0), 1e-9)
  }

  @Test
  fun `a one metre built ledge is refused where one metre of terrain is not`() {
    val ground = flatGround()
    ground[5, 5, 4] = BlockType.GRANITE

    val tile = WalkableTile.of(ground, agent, cells { block(2, 2, 4) })

    assertEquals(-1.0, tile.stepTarget(2, 2, fromSurface = 4.0), 1e-9, "a table is not a stair")
    assertEquals(5.0, tile.stepTarget(5, 5, fromSurface = 4.0), 1e-9, "a terrain rise of a metre is a slope")
  }

  @Test
  fun `a half metre built step is walkable both ways`() {
    val tile = WalkableTile.of(flatGround(), agent, cells { block(2, 2, 4, fraction = 0.5) })

    assertEquals(4.5, tile.stepTarget(2, 2, fromSurface = 4.0), 0.01)
    assertEquals(4.0, tile.stepTarget(1, 2, fromSurface = 4.5, fromStructure = true), 0.01)
  }

  @Test
  fun `stairs of half metre steps lead up to a floor three metres high`() {
    val stairs = cells {
      for (step in 1..6) {
        val rise = step * 0.5
        val fullBlocks = rise.toInt()
        for (z in 4 until 4 + fullBlocks) block(step, 5, z)
        if (rise > fullBlocks) block(step, 5, 4 + fullBlocks, fraction = 0.5)
      }
      for (z in 4..6) block(7, 5, z)
    }
    val store = DerivedStore(voxels = { flatGround() }, agent = agent, structures = { stairs })

    var surface = 4.0
    for (x in 1..7) {
      val crossing = Edges.crossedBy(1, 0)
      assertTrue(store.canStep(pos, x - 1, 5, surface, pos, x, 5, crossing = crossing), "step onto column $x")
      surface = store.walkableOf(pos).stepTarget(x, 5, surface, fromStructure = x > 1)
    }

    assertEquals(7.0, surface, 0.01, "standing on the upper floor")
  }

  @Test
  fun `a wall on a border refuses the step across it from either side`() {
    val walls = cells { wallTowardsXPlus(2, 2, 4) }
    val store = DerivedStore(voxels = { flatGround() }, agent = agent, structures = { walls })

    assertFalse(store.canStep(pos, 2, 2, 4.0, pos, 3, 2, crossing = Edges.X_PLUS))
    assertFalse(store.canStep(pos, 3, 2, 4.0, pos, 2, 2, crossing = Edges.X_MINUS))
    assertTrue(store.canStep(pos, 2, 2, 4.0, pos, 2, 3, crossing = Edges.Y_PLUS), "along the wall is open")
  }

  @Test
  fun `a diagonal past the end of a wall touches it`() {
    val walls = cells { wallTowardsXPlus(2, 2, 4) }
    val store = DerivedStore(voxels = { flatGround() }, agent = agent, structures = { walls })

    assertFalse(store.canStep(pos, 2, 1, 4.0, pos, 3, 2, crossing = Edges.crossedBy(1, 1)))
  }

  @Test
  fun `a one metre fence on a border blocks, though a metre of terrain would not`() {
    val tile = WalkableTile.of(flatGround(), agent, cells { wallTowardsXPlus(2, 2, 4) })

    assertEquals(Edges.X_PLUS, tile.wallsOf(tile.spanAt(2, 2, 3)))
    assertEquals(Edges.X_MINUS, tile.wallsOf(tile.spanAt(3, 2, 3)))
  }

  @Test
  fun `a wall above the head blocks nobody under it`() {
    val tile = WalkableTile.of(flatGround(), agent, cells { wallTowardsXPlus(2, 2, 6) })

    assertEquals(Edges.NONE, tile.wallsOf(tile.spanAt(2, 2, 3)), "a body standing at 4.0 fills voxels 4 and 5")
  }

  @Test
  fun `a wall in the slab above reaches the head of a floor at the top of this one`() {
    val ground = VoxelChunk(pos, size, height)
    for (y in 0 until size) {
      for (x in 0 until size) {
        for (z in 0 until height) ground[x, y, z] = BlockType.GRANITE
      }
    }
    val overhead = cells { wallTowardsXPlus(2, 2, 0) }

    val tile = WalkableTile.of(ground, agent, cellsAbove = overhead) { VoxelChunk(pos.copy(z = 1), size, height) }

    assertEquals(Edges.X_PLUS, tile.wallsOf(tile.spanAt(2, 2, height - 1)))
  }

  @Test
  fun `no pieces give the same tile as no overlay at all`() {
    val ground = flatGround()
    ground[1, 1, 4] = BlockType.GRANITE

    val plain = WalkableTile.of(ground, agent)
    val overlaid = WalkableTile.of(ground, agent, cells { })

    for (y in 0 until size) {
      for (x in 0 until size) {
        assertContentEquals(plain.surfacesAt(x, y), overlaid.surfacesAt(x, y), "column ($x,$y)")
      }
    }
  }

  @Test
  fun `quarter turns walk the edges round counter-clockwise`() {
    assertEquals(Edges.Y_PLUS, Edges.rotated(Edges.X_PLUS, 1))
    assertEquals(Edges.X_PLUS, Edges.rotated(Edges.Y_MINUS, 1))
    assertEquals(Edges.X_MINUS or Edges.Y_MINUS, Edges.opposite(Edges.X_PLUS or Edges.Y_PLUS))
    assertEquals(Edges.Y_MINUS, Edges.rotated(Edges.X_PLUS, -1))
  }
}
