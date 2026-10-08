package net.bestia.zone.water.sim

import net.bestia.worldgen.voxel.Occupancy
import java.util.PriorityQueue

/**
 * Pours a still body of water back into the space it stands in, lowest cells first, so its surface is flat at
 * once.
 *
 * The automaton gets there itself only slowly or not at all: pair-averaging flattens a body of width W in about
 * W²/5 passes, and it cannot push water up the far leg of a U-bend. A body is the water connected to a seed
 * through faces. Its volume is poured back by a priority flood from its lowest cell through open cells, and the
 * last layer shares what is left.
 *
 * A body is left alone when it touches generated water, which keeps its own level, or a chunk that is not held,
 * where water could still leave. So is one with an open drop at its edge: water there still has somewhere to
 * fall, which is the automaton's job.
 */
class WaterLeveller(private val maxCells: Int) {

  /** One cell in whichever chunk holds it, with its height in the world for the flood's order. */
  private class Cell(val chunk: WaterChunk, val index: Int, val z: Long, val order: Long)

  /** @return whether the body around [seed] was levelled */
  fun level(chunk: WaterChunk, seed: Int): Boolean {
    if (chunk.isSource(seed) || chunk.fillAt(seed) == 0) return false

    val body = bodyAround(chunk, seed) ?: return false
    val volume = body.sumOf { it.chunk.fillAt(it.index).toLong() }
    val lowest = body.minWith(compareBy({ it.z }, { it.order }))

    val levels = flood(lowest, volume) ?: return false

    for (cell in body) cell.chunk.setFill(cell.index, Occupancy.EMPTY)
    for ((cell, fill) in levels) cell.chunk.setFill(cell.index, fill)
    return true
  }

  /** The water connected to [seed], or null if it touches something that keeps it from being levelled. */
  private fun bodyAround(chunk: WaterChunk, seed: Int): List<Cell>? {
    val seen = HashMap<WaterChunk, java.util.BitSet>()
    val queue = ArrayDeque<Cell>()
    val body = ArrayList<Cell>()
    var order = 0L

    fun visit(owner: WaterChunk, index: Int): Boolean {
      val marks = seen.getOrPut(owner) { java.util.BitSet(owner.volume) }
      if (marks[index]) return false
      marks.set(index)
      return true
    }

    visit(chunk, seed)
    queue.add(cellOf(chunk, seed, order++))

    while (queue.isNotEmpty()) {
      val cell = queue.removeFirst()
      body.add(cell)
      if (body.size > maxCells) return null

      for (face in Face.entries) {
        val owner = cell.chunk.neighbourChunk(cell.index, face) ?: return null
        val index = cell.chunk.neighbourIndex(cell.index, face)
        if (owner.isSource(index)) return null
        if (owner.isWall(index) || owner.fillAt(index) == 0) continue
        if (visit(owner, index)) queue.add(cellOf(owner, index, order++))
      }
    }

    return body
  }

  /**
   * Pours [volume] into the open cells reachable from [start], lowest first. Null when the pour would reach
   * generated water, a chunk that is not held, an open drop, or more than [maxCells] cells.
   */
  private fun flood(start: Cell, volume: Long): List<Pair<Cell, Int>>? {
    val seen = HashMap<WaterChunk, java.util.BitSet>()
    val heap = PriorityQueue<Cell>(compareBy({ it.z }, { it.order }))
    var order = 0L

    fun push(owner: WaterChunk, index: Int) {
      val marks = seen.getOrPut(owner) { java.util.BitSet(owner.volume) }
      if (marks[index]) return
      marks.set(index)
      heap.add(cellOf(owner, index, order++))
    }

    push(start.chunk, start.index)

    val filled = ArrayList<Pair<Cell, Int>>()
    var left = volume
    var level = Long.MIN_VALUE
    val layer = ArrayList<Cell>()

    while (heap.isNotEmpty()) {
      val cell = heap.peek()

      if (cell.z != level) {
        // Reaching lower after rising means an open drop: water there would still fall.
        if (cell.z < level) return null

        // A layer is complete once the flood rises past it. Fill it, or share what is left across it.
        val capacity = layer.size.toLong() * Occupancy.FULL
        if (layer.isNotEmpty() && left <= capacity) return filled + share(layer, left)
        layer.forEach { filled.add(it to Occupancy.FULL) }
        left -= capacity
        layer.clear()
        level = cell.z
      }

      heap.poll()
      layer.add(cell)
      if (filled.size + layer.size > maxCells) return null

      for (face in Face.entries) {
        val owner = cell.chunk.neighbourChunk(cell.index, face) ?: return null
        val index = cell.chunk.neighbourIndex(cell.index, face)
        if (owner.isSource(index)) return null
        if (!owner.isWall(index)) push(owner, index)
      }
    }

    // A closed space: the last layer is complete when nothing is left to flood.
    val capacity = layer.size.toLong() * Occupancy.FULL
    return if (layer.isNotEmpty() && left <= capacity) filled + share(layer, left) else null
  }

  /** [volume] shared across [layer] as evenly as whole units allow, the first cells taking the remainder. */
  private fun share(layer: List<Cell>, volume: Long): List<Pair<Cell, Int>> {
    val each = (volume / layer.size).toInt()
    val extra = (volume % layer.size).toInt()

    return layer.mapIndexed { at, cell -> cell to each + if (at < extra) 1 else 0 }
  }

  private fun cellOf(chunk: WaterChunk, index: Int, order: Long): Cell {
    return Cell(chunk, index, chunk.pos.z.toLong() * chunk.height + index % chunk.height, order)
  }
}
