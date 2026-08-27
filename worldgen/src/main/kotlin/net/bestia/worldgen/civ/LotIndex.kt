package net.bestia.worldgen.civ

import net.bestia.worldgen.vector.Vec2d
import kotlin.math.abs

/**
 * A bucket grid over placed plots, for the overlap test.
 *
 * The same shape as the settlement stage's separation index and for the same reason: a town lays a few
 * thousand candidate plots and rejects most of them for touching one already there, and testing each against
 * every placed plot is quadratic in the longest loop of the stage.
 *
 * **It is the only thing keeping two buildings out of the same ground**, so every producer of a [Lot] has to
 * go through one - `ConvexPolygons.orientedExtent`'s own KDoc names this index as the guarantee, and for a
 * while [BlockSubdivider] was the producer that did not consult it: a cut leaf is a trapezoid and its plot is
 * the leaf's *bounding* rectangle, which reaches over the alley into the neighbour. Measured on a 160-cell
 * demo world, 299 of 2531 buildings intersected another one. Hence one index per town, shared by both
 * producers, rather than one per producer.
 *
 * [cellMetres] must be at least the largest centre-to-centre distance two overlapping plots can have, since
 * [overlaps] only searches the candidate's own cell and its eight neighbours. Two plots overlap only if their
 * centres are within `(halfFrontage + halfDepth)` of each other summed over the pair, so twice the largest
 * such sum is always safe.
 */
internal class LotIndex(private val cellMetres: Double) {

  private val buckets = HashMap<Long, ArrayList<Lot>>()

  fun add(lot: Lot) {
    buckets.getOrPut(keyOf(lot.centre)) { ArrayList() }.add(lot)
  }

  fun overlaps(lot: Lot): Boolean {
    val bx = Math.floor(lot.centre.x / cellMetres).toLong()
    val by = Math.floor(lot.centre.y / cellMetres).toLong()

    for (dy in -1..1) {
      for (dx in -1..1) {
        val bucket = buckets[key(bx + dx, by + dy)] ?: continue
        for (other in bucket) {
          if (intersects(lot, other)) return true
        }
      }
    }
    return false
  }

  /**
   * Oriented-box intersection by the separating-axis theorem.
   *
   * Four axes - each box's two - and the boxes are apart if any one of them separates them. A bounding-circle
   * test would be far simpler and is not usable here: consecutive plots on the same street are nine metres
   * apart and their circumscribed circles are nine metres across, so a circle test rejects every plot's own
   * neighbour and a town comes out with every other plot empty.
   */
  private fun intersects(a: Lot, b: Lot): Boolean {
    val axes = arrayOf(a.inwards.perpendicular(), a.inwards, b.inwards.perpendicular(), b.inwards)
    for (axis in axes) {
      val centreGap = abs((b.centre - a.centre) dot axis)
      val spread = a.extentAlong(axis) + b.extentAlong(axis)
      if (centreGap > spread) return false
    }
    return true
  }

  private fun keyOf(at: Vec2d): Long {
    return key(Math.floor(at.x / cellMetres).toLong(), Math.floor(at.y / cellMetres).toLong())
  }

  private fun key(bx: Long, by: Long): Long {
    return (bx shl 32) xor (by and 0xFFFF_FFFFL)
  }
}
