package net.bestia.zone.water.sim

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.Occupancy
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * What one updated cell costs: the number behind `water.cells-per-step`. A report, not a check - enable it when
 * changing the stepper or that budget.
 */
@Disabled("Tuning report, not a check. See the class note.")
class WaterStepCalibrationTest {

  @Test
  fun `nanoseconds per updated cell on a spreading flood`() {
    // Full-size chunks, two by two, with a floor: a flood spreading over open ground, the common expensive case.
    val volume = WaterVolume()
    val chunks = (0 until 2).flatMap { y -> (0 until 2).map { x -> WaterChunk(ChunkPos(x, y, 0), SIZE, HEIGHT) } }
    for (chunk in chunks) {
      for (column in 0 until SIZE * SIZE) chunk.loadWall(column * HEIGHT)
      volume.add(chunk)
    }

    val first = chunks.first()
    for (column in 0 until SIZE * SIZE / 4) {
      for (z in 1..8) {
        first.loadFill(column * HEIGHT + z, Occupancy.FULL)
        first.wake(column * HEIGHT + z)
      }
    }

    val stepper = WaterStepper(volume)
    // Warm the JIT on the first stretch, then report on the second.
    repeat(200) { stepper.step(BUDGET) }

    var cells = 0L
    val start = System.nanoTime()
    repeat(200) { cells += stepper.step(BUDGET) }
    val nanos = System.nanoTime() - start

    println("updated $cells cells in ${nanos / 1_000_000} ms: ${nanos / maxOf(cells, 1)} ns per cell")
  }

  private companion object {
    const val SIZE = 32
    const val HEIGHT = 256
    const val BUDGET = 20_000
  }
}
