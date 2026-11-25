package net.bestia.zone.worldevent.rift

import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.mana.ManaFieldRule
import kotlin.math.hypot

/**
 * What a rift does to the mana field: pull it in, measure the land around it, and spill it all out again. Pure
 * over the field, so `RiftCalibrationTest` can run a whole rift on a test field. The caller holds the field's lock.
 */
class RiftField(
  private val field: ManaField,
  private val ground: ManaFieldRule.Ground,
) {

  /**
   * Moves [share] of each cell's mana above [floor] into the rift, full at the centre and half at [radius].
   * @return the motes pulled in
   */
  fun pull(x: Double, y: Double, radius: Double, share: Double, floor: Double): Double {
    var taken = 0.0
    field.cellsWithin(x, y, radius).forEach { cell ->
      val density = ground.startDensity(cell) + field.deltaOf(cell.x, cell.y)
      val above = density - floor
      if (above <= 0.0) return@forEach

      val weight = 1.0 - 0.5 * hypot(cell.centreX - x, cell.centreY - y) / radius
      val delta = above * share * weight
      field.setDelta(cell, field.deltaOf(cell.x, cell.y) - delta)
      taken += delta / ManaField.DELTA_PER_MOTE
    }
    return taken
  }

  /** The mean density of the ring between [inner] and twice that: the land a knot draws on. */
  fun ringDensity(x: Double, y: Double, inner: Double): Double {
    val ring = field.cellsWithin(x, y, 2 * inner).filter { hypot(it.centreX - x, it.centreY - y) > inner }
    if (ring.isEmpty()) return 0.0
    return ring.sumOf { ground.startDensity(it) + field.deltaOf(it.x, it.y) } / ring.size
  }

  /** Lets [motes] go around ([x], [y]), most of it at the centre and none past [radius]. */
  fun spill(x: Double, y: Double, radius: Double, motes: Double) {
    val cells = field.cellsWithin(x, y, radius)
    val weights = cells.map { cell ->
      val falloff = 1.0 - hypot(cell.centreX - x, cell.centreY - y) / (radius + ManaField.CELL_METRES)
      falloff * falloff
    }
    val total = weights.sum()
    cells.forEachIndexed { i, cell -> field.addToCell(cell, motes * weights[i] / total) }
  }
}
