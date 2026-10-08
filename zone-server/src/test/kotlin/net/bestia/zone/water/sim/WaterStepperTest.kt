package net.bestia.zone.water.sim

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.Occupancy
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaterStepperTest {

  private fun index(x: Int, y: Int, z: Int): Int {
    return (y * SIZE + x) * HEIGHT + z
  }

  /** Open cells with a wall for a floor. The chunk's edges hold water in, since nothing is loaded beyond them. */
  private fun basin(pos: ChunkPos = ChunkPos(0, 0, 0), floor: Boolean = true): WaterChunk {
    val chunk = WaterChunk(pos, SIZE, HEIGHT)
    if (floor) {
      for (y in 0 until SIZE) {
        for (x in 0 until SIZE) {
          chunk.loadWall(index(x, y, 0))
        }
      }
    }
    return chunk
  }

  private fun pour(chunk: WaterChunk, x: Int, y: Int, z: Int, level: Int = Occupancy.FULL) {
    chunk.loadFill(index(x, y, z), level)
    chunk.wake(index(x, y, z))
  }

  private fun volumeOf(vararg chunks: WaterChunk): WaterVolume {
    val volume = WaterVolume()
    for (chunk in chunks) volume.add(chunk)
    return volume
  }

  /** Steps until every chunk sleeps, and fails a test that never settles rather than hanging it. */
  private fun settle(volume: WaterVolume, budget: Int = 1_000): WaterStepper {
    val stepper = WaterStepper(volume)
    repeat(100_000) {
      if (stepper.step(budget) < budget) return stepper
    }
    error("the water never settled")
  }

  private fun movable(volume: WaterVolume): Long {
    return volume.all().sumOf { it.movableWater() }
  }

  @Test
  fun `water falls to the floor and none is lost`() {
    val chunk = basin()
    pour(chunk, 1, 1, 6)
    val volume = volumeOf(chunk)

    settle(volume)

    assertEquals(Occupancy.FULL.toLong(), movable(volume))
    for (index in 0 until chunk.volume) {
      if (index % HEIGHT > 1) assertEquals(0, chunk.fillAt(index), "water stayed above the floor at $index")
    }
  }

  @Test
  fun `water on a floor spreads out flat`() {
    val chunk = basin()
    for (y in 0 until SIZE / 2) {
      for (x in 0 until SIZE) {
        pour(chunk, x, y, 1)
      }
    }
    val volume = volumeOf(chunk)
    val before = movable(volume)

    settle(volume)

    assertEquals(before, movable(volume))
    for (y in 0 until SIZE) {
      for (x in 0 until SIZE - 1) {
        val step = abs(chunk.fillAt(index(x, y, 1)) - chunk.fillAt(index(x + 1, y, 1)))
        assertTrue(step <= 1, "neighbours at ($x,$y) differ by $step")
      }
    }
  }

  @Test
  fun `settled water sleeps and stays put`() {
    val chunk = basin()
    pour(chunk, 2, 2, 5)
    val volume = volumeOf(chunk)
    val stepper = settle(volume)
    val settled = ByteArray(chunk.volume) { chunk.fillAt(it).toByte() }

    assertTrue(chunk.isAsleep)
    assertEquals(0, stepper.step(1_000))
    assertContentEquals(settled, ByteArray(chunk.volume) { chunk.fillAt(it).toByte() })
  }

  @Test
  fun `a trench beside generated water fills to its level`() {
    val chunk = basin()
    // A river along x = 0, three voxels deep, behind a bank. One trench cell column cut through the bank.
    for (y in 0 until SIZE) {
      for (z in 1..3) {
        chunk.loadSource(index(0, y, z), Occupancy.FULL)
        for (x in 1 until SIZE) {
          if (!(x == 1 && y == 1)) chunk.loadWall(index(x, y, z))
        }
      }
    }
    for (z in 1..3) chunk.wake(index(1, 1, z))
    val volume = volumeOf(chunk)

    settle(volume)

    for (z in 1..3) {
      assertTrue(chunk.fillAt(index(1, 1, z)) >= Occupancy.FULL - 1, "the trench at z=$z is not full")
    }
  }

  @Test
  fun `a staircase of generated water stays still`() {
    val chunk = basin()
    // A river stepping down along x, walled in at its foot.
    for (y in 0 until SIZE) {
      for (z in 1..3) chunk.loadSource(index(0, y, z), Occupancy.FULL)
      for (z in 1..2) chunk.loadSource(index(1, y, z), Occupancy.FULL)
      chunk.loadSource(index(2, y, 1), Occupancy.FULL)
      for (z in 1..3) chunk.loadWall(index(3, y, z))
    }
    for (index in 0 until chunk.volume) chunk.wake(index)
    val volume = volumeOf(chunk)

    settle(volume)

    assertEquals(0L, movable(volume), "a sloped river must not spill over its own steps")
  }

  @Test
  fun `water landing on generated water joins it`() {
    val chunk = basin()
    for (y in 0 until SIZE) {
      for (x in 0 until SIZE) {
        chunk.loadSource(index(x, y, 1), Occupancy.FULL)
      }
    }
    pour(chunk, 2, 2, 5)
    val volume = volumeOf(chunk)

    settle(volume)

    assertEquals(0L, movable(volume))
  }

  @Test
  fun `water crosses a chunk border and none is lost`() {
    val west = basin(ChunkPos(0, 0, 0))
    val east = basin(ChunkPos(1, 0, 0))
    for (y in 0 until SIZE) pour(west, SIZE - 1, y, 1)
    val volume = volumeOf(west, east)
    val before = movable(volume)

    settle(volume)

    assertEquals(before, movable(volume))
    assertTrue(east.movableWater() > 0, "no water reached the eastern chunk")
  }

  @Test
  fun `water falls from one chunk into the one below`() {
    val upper = basin(ChunkPos(0, 0, 1), floor = false)
    val lower = basin(ChunkPos(0, 0, 0))
    pour(upper, 1, 1, 0)
    val volume = volumeOf(upper, lower)

    settle(volume)

    assertEquals(0L, upper.movableWater())
    assertEquals(Occupancy.FULL.toLong(), lower.movableWater())
  }

  @Test
  fun `the budget per call does not change where the water ends up`() {
    fun settled(budget: Int): ByteArray {
      val chunk = basin()
      pour(chunk, 0, 0, 6)
      pour(chunk, 3, 3, 4, level = 100)
      settle(volumeOf(chunk), budget)
      return ByteArray(chunk.volume) { chunk.fillAt(it).toByte() }
    }

    assertContentEquals(settled(1_000), settled(1))
  }

  @Test
  fun `water pressing against a chunk that is not held asks for it`() {
    val chunk = basin()
    pour(chunk, SIZE - 1, 1, 1)
    val volume = volumeOf(chunk)

    settle(volume)

    assertTrue(ChunkPos(1, 0, 0) in volume.wanted)
  }

  private companion object {
    const val SIZE = 4
    const val HEIGHT = 8
  }
}
