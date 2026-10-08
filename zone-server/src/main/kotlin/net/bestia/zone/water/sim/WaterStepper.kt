package net.bestia.zone.water.sim

import net.bestia.worldgen.voxel.Occupancy
import kotlin.math.min

/**
 * Moves the water of a [WaterVolume], pass by pass, under a budget of cells per call.
 *
 * A cellular automaton over fill levels. Water falls first, and spreads sideways only where it cannot fall, by
 * pair-averaging with lower neighbours. Every move between two cells is a pairwise average, so the sum of squared
 * fills strictly falls and enclosed water always settles. Generated water is a source of fixed level: it fills
 * open cells beside and below it, takes what flows or falls into it, and never changes itself.
 *
 * Integer arithmetic in a fixed order, so the same volume stepped with the same budgets ends in the same state.
 */
class WaterStepper(private val volume: WaterVolume) {

  private var pass: List<WaterChunk> = emptyList()
  private var cursorChunk = 0
  private var cursorCell = 0

  /** Passes started. Rotates which side a cell spreads to first, so no side is favoured. */
  var passes = 0L
    private set

  /**
   * Updates up to [budget] active cells, resuming where the last call stopped.
   *
   * @return how many cells were updated; fewer than [budget] means every chunk is asleep
   */
  fun step(budget: Int): Int {
    var updated = 0

    while (updated < budget) {
      if (cursorChunk >= pass.size) {
        if (!beginPass()) return updated
        continue
      }

      val chunk = pass[cursorChunk]
      val index = chunk.active.nextSetBit(cursorCell)
      if (index < 0) {
        cursorChunk++
        cursorCell = 0
        continue
      }

      chunk.active.clear(index)
      cursorCell = index + 1
      update(chunk, index)
      updated++
    }

    return updated
  }

  /** Ends the running pass and starts the next. @return false when nothing anywhere is awake */
  private fun beginPass(): Boolean {
    for (chunk in volume.all()) {
      chunk.endPass()
    }

    pass = volume.all().filter { !it.active.isEmpty }.sortedBy { it.pos.key() }
    cursorChunk = 0
    cursorCell = 0
    passes++

    return pass.isNotEmpty()
  }

  private fun update(chunk: WaterChunk, index: Int) {
    if (chunk.isWall(index) || chunk.isSource(index)) return

    val belowChunk = chunk.neighbourChunk(index, Face.DOWN)
    val below = chunk.neighbourIndex(index, Face.DOWN)

    // Resting on generated water: what lands here joins it, and nothing is drawn sideways to sit here. Without
    // the second half, every step down a sloped river would draw a sliver over its edge for ever.
    if (belowChunk != null && belowChunk.isSource(below)) {
      change(chunk, index, Occupancy.EMPTY)
      return
    }

    var fill = drawFromSources(chunk, index, chunk.fillAt(index))
    fill = fall(chunk, index, fill, belowChunk, below)

    if (fill >= MIN_SPREAD) {
      fill = spread(chunk, index, fill)
    }

    change(chunk, index, fill)
  }

  private fun drawFromSources(chunk: WaterChunk, index: Int, start: Int): Int {
    var fill = start

    val aboveChunk = chunk.neighbourChunk(index, Face.UP)
    val above = chunk.neighbourIndex(index, Face.UP)
    if (aboveChunk != null && aboveChunk.isSource(above)) {
      fill = min(Occupancy.FULL, fill + aboveChunk.fillAt(above))
    }

    for (face in Face.LATERAL) {
      val neighbourChunk = chunk.neighbourChunk(index, face) ?: continue
      val neighbour = chunk.neighbourIndex(index, face)
      if (!neighbourChunk.isSource(neighbour)) continue

      val level = neighbourChunk.fillAt(neighbour)
      if (level - fill >= 2) fill += (level - fill) / 2
    }

    return fill
  }

  /** @return what is left in the cell; anything left means the cell below is full, a wall, or not held */
  private fun fall(chunk: WaterChunk, index: Int, fill: Int, belowChunk: WaterChunk?, below: Int): Int {
    if (fill == 0) return 0

    if (belowChunk == null) {
      volume.wanted.add(volume.neighbourOf(chunk.pos, Face.DOWN))
      return fill
    }
    if (belowChunk.isWall(below)) return fill

    val belowFill = belowChunk.fillAt(below)
    val moved = min(fill, Occupancy.FULL - belowFill)
    if (moved > 0) change(belowChunk, below, belowFill + moved)

    return fill - moved
  }

  private fun spread(chunk: WaterChunk, index: Int, start: Int): Int {
    var fill = start
    val first = (passes % Face.LATERAL.size).toInt()

    for (offset in Face.LATERAL.indices) {
      if (fill < MIN_SPREAD) break

      val face = Face.LATERAL[(first + offset) % Face.LATERAL.size]
      val neighbourChunk = chunk.neighbourChunk(index, face)
      if (neighbourChunk == null) {
        volume.wanted.add(volume.neighbourOf(chunk.pos, face))
        continue
      }

      val neighbour = chunk.neighbourIndex(index, face)
      if (neighbourChunk.isWall(neighbour)) continue

      val level = neighbourChunk.fillAt(neighbour)
      if (fill - level < 2) continue

      val moved = (fill - level) / 2
      fill -= moved
      // Into generated water it is gone: the source keeps its level.
      if (!neighbourChunk.isSource(neighbour)) change(neighbourChunk, neighbour, level + moved)
    }

    return fill
  }

  private fun change(chunk: WaterChunk, index: Int, value: Int) {
    if (chunk.fillAt(index) == value) return

    chunk.setFill(index, value)
    chunk.wake(index)
  }

  companion object {
    /** About six centimetres. Water thinner than this stays where it is, so a flood ends instead of filming out. */
    const val MIN_SPREAD = 16
  }
}
