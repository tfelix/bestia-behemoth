package net.bestia.zone.structure

import java.util.PriorityQueue

/**
 * How stable each standing piece is, so nothing is placed hanging in the air.
 *
 * Two pieces touch when a cell of one is in, beside, above or below a cell of the other. A piece is carried by the
 * best of the pieces it touches, less its [StructureMaterial] loss for that contact; a piece with a cell on terrain
 * is carried by the ground at 1.
 *
 * Not thread safe: owned by the tick thread.
 */
class StructureSupport {

  private class Placed(val material: StructureMaterial, val cells: List<StructureCell>, val grounded: Boolean)

  private val pieces = HashMap<Long, Placed>()
  private val piecesAt = HashMap<Long, MutableList<Long>>()
  private val stability = HashMap<Long, Double>()

  fun stabilityOf(pieceId: Long): Double {
    return stability[pieceId] ?: 0.0
  }

  /** How stable a piece would stand with these cells, against the pieces standing now. */
  fun stabilityIfPlaced(material: StructureMaterial, cells: List<StructureCell>, grounded: Boolean): Double {
    if (grounded) return GROUND

    var best = 0.0
    forEachContact(cells) { other, vertical ->
      val loss = if (vertical) material.verticalLoss else material.horizontalLoss
      best = maxOf(best, stabilityOf(other) - loss)
    }

    return best
  }

  /** Adds a piece and settles the stability of everything it touches. */
  fun add(pieceId: Long, material: StructureMaterial, cells: List<StructureCell>, grounded: Boolean) {
    addWithoutSettling(pieceId, material, cells, grounded)
    settle(setOf(pieceId))
  }

  /** Adds many pieces at once, settling once at the end: boot restores a whole world of them. */
  fun addAll(placed: List<PlacedPiece>) {
    placed.forEach { addWithoutSettling(it.pieceId, it.material, it.cells, it.grounded) }
    settle(placed.mapTo(HashSet()) { it.pieceId })
  }

  /**
   * Removes a piece and settles the stability of what it held.
   *
   * @return the pieces now below their material's minimum, which nothing holds any more
   */
  fun remove(pieceId: Long): Set<Long> {
    val removed = pieces.remove(pieceId) ?: return emptySet()
    stability.remove(pieceId)

    val neighbours = HashSet<Long>()
    for (cell in removed.cells) {
      val key = keyOf(cell.x, cell.y, cell.z)
      piecesAt[key]?.let { here ->
        here.remove(pieceId)
        if (here.isEmpty()) piecesAt.remove(key)
      }
    }
    forEachContact(removed.cells) { other, _ -> neighbours.add(other) }

    settle(neighbours)

    return neighbours.flatMapTo(HashSet()) { componentOf(it) }
      .filterTo(HashSet()) { stabilityOf(it) < pieces.getValue(it).material.minStability }
  }

  /** One piece to add through [addAll]. */
  data class PlacedPiece(
    val pieceId: Long,
    val material: StructureMaterial,
    val cells: List<StructureCell>,
    val grounded: Boolean
  )

  private fun addWithoutSettling(pieceId: Long, material: StructureMaterial, cells: List<StructureCell>, grounded: Boolean) {
    pieces[pieceId] = Placed(material, cells, grounded)
    for (cell in cells) {
      piecesAt.getOrPut(keyOf(cell.x, cell.y, cell.z)) { ArrayList(1) }.add(pieceId)
    }
  }

  /**
   * Recomputes every piece connected to [changed], from the ground up: the most stable pieces pass their stability
   * on first, so each piece ends with the best chain of contacts to the ground it has.
   */
  private fun settle(changed: Set<Long>) {
    val component = changed.filter { it in pieces }.flatMapTo(HashSet()) { componentOf(it) }
    component.forEach { stability[it] = 0.0 }

    val queue = PriorityQueue<Pair<Long, Double>>(compareByDescending { it.second })
    for (pieceId in component) {
      if (pieces.getValue(pieceId).grounded) {
        stability[pieceId] = GROUND
        queue.add(pieceId to GROUND)
      }
    }

    while (queue.isNotEmpty()) {
      val (pieceId, carried) = queue.poll()
      if (carried < stabilityOf(pieceId)) continue

      forEachContact(pieces.getValue(pieceId).cells) { other, vertical ->
        if (other == pieceId) return@forEachContact

        val material = pieces.getValue(other).material
        val passed = carried - if (vertical) material.verticalLoss else material.horizontalLoss
        if (passed > stabilityOf(other)) {
          stability[other] = passed
          queue.add(other to passed)
        }
      }
    }
  }

  private fun componentOf(start: Long): Set<Long> {
    val seen = hashSetOf(start)
    val open = ArrayDeque(listOf(start))
    while (open.isNotEmpty()) {
      val pieceId = open.removeFirst()
      forEachContact(pieces.getValue(pieceId).cells) { other, _ ->
        if (seen.add(other)) open.addLast(other)
      }
    }

    return seen
  }

  /**
   * Every piece a cell of [cells] touches, and whether it is above or below rather than beside. Includes the piece
   * the cells belong to, if it stands, so a caller skips it.
   */
  private inline fun forEachContact(cells: List<StructureCell>, action: (Long, Boolean) -> Unit) {
    for (cell in cells) {
      for ((dx, dy, dz) in NEIGHBOURS) {
        val others = piecesAt[keyOf(cell.x + dx, cell.y + dy, cell.z + dz)] ?: continue
        for (other in others) action(other, dz != 0)
      }
    }
  }

  private companion object {
    const val GROUND = 1.0

    /** The cell itself, the four beside it, and the ones above and below. */
    val NEIGHBOURS = listOf(
      Triple(0, 0, 0),
      Triple(1, 0, 0), Triple(-1, 0, 0), Triple(0, 1, 0), Triple(0, -1, 0),
      Triple(0, 0, 1), Triple(0, 0, -1)
    )

    fun keyOf(x: Long, y: Long, z: Long): Long {
      return ((x and 0xFFFFFF) shl 40) or ((y and 0xFFFFFF) shl 16) or (z and 0xFFFF)
    }
  }
}
