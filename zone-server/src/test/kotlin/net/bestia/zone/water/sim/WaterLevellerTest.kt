package net.bestia.zone.water.sim

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.Occupancy
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaterLevellerTest {

  private fun index(x: Int, y: Int, z: Int): Int {
    return (y * SIZE + x) * HEIGHT + z
  }

  /**
   * Walls everywhere, so a test carves out exactly the space it wants water to stand in. Shapes stay off the
   * chunk's edges: across an edge nothing is held, and the leveller leaves such water alone.
   */
  private fun solid(): WaterChunk {
    val chunk = WaterChunk(ChunkPos(0, 0, 0), SIZE, HEIGHT)
    for (index in 0 until chunk.volume) chunk.loadWall(index)
    return chunk
  }

  private fun open(chunk: WaterChunk, x: Int, y: Int, z: Int, fill: Int = 0) {
    chunk.loadOpen(index(x, y, z), fill)
  }

  private fun total(chunk: WaterChunk): Long {
    return chunk.movableWater()
  }

  @Test
  fun `the two legs of a U-bend end at the same level`() {
    val chunk = solid()
    // Legs at x = 1 and x = 4, joined along the bottom at z = 2. The left leg holds water up to z = 7.
    for (x in 1..4) open(chunk, x, 2, 2, fill = Occupancy.FULL)
    for (z in 3..8) {
      open(chunk, 1, 2, z, fill = if (z <= 7) Occupancy.FULL else 0)
      open(chunk, 4, 2, z)
    }
    val before = total(chunk)

    assertTrue(WaterLeveller(maxCells = 1_000).level(chunk, index(1, 2, 7)))

    assertEquals(before, total(chunk))
    for (z in 3..8) {
      // The last layer's remainder goes to its first cells, so the legs may differ by one unit there.
      val step = abs(chunk.fillAt(index(1, 2, z)) - chunk.fillAt(index(4, 2, z)))
      assertTrue(step <= 1, "the legs differ by $step at z=$z")
    }
  }

  @Test
  fun `a sloping surface comes out flat`() {
    val chunk = solid()
    // A closed basin two cells deep; the water stands higher on the west than on the east.
    for (y in 1..4) {
      for (x in 1..4) {
        open(chunk, x, y, 2, fill = Occupancy.FULL)
        open(chunk, x, y, 3, fill = 40 + 10 * x)
        open(chunk, x, y, 4)
      }
    }
    val before = total(chunk)

    assertTrue(WaterLeveller(maxCells = 1_000).level(chunk, index(1, 1, 3)))

    assertEquals(before, total(chunk))
    val top = (1..4).flatMap { y -> (1..4).map { x -> chunk.fillAt(index(x, y, 3)) } }
    assertTrue(top.max() - top.min() <= 1, "the surface still slopes: $top")
  }

  @Test
  fun `water touching generated water is left alone`() {
    val chunk = solid()
    open(chunk, 1, 1, 2, fill = 200)
    open(chunk, 2, 1, 2)
    chunk.loadSource(index(3, 1, 2), Occupancy.FULL)

    assertFalse(WaterLeveller(maxCells = 1_000).level(chunk, index(1, 1, 2)))
    assertEquals(200, chunk.fillAt(index(1, 1, 2)))
  }

  @Test
  fun `water with an open drop beside it is left to fall`() {
    val chunk = solid()
    open(chunk, 1, 1, 5, fill = 200)
    open(chunk, 2, 1, 5)
    open(chunk, 2, 1, 4)
    open(chunk, 2, 1, 3)

    assertFalse(WaterLeveller(maxCells = 1_000).level(chunk, index(1, 1, 5)))
  }

  @Test
  fun `a body larger than the cap is left alone`() {
    val chunk = solid()
    for (x in 1..4) open(chunk, x, 1, 2, fill = 100)

    assertFalse(WaterLeveller(maxCells = 2).level(chunk, index(1, 1, 2)))
  }

  private companion object {
    const val SIZE = 6
    const val HEIGHT = 10
  }
}
