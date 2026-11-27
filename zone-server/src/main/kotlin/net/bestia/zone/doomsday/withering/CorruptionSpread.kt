package net.bestia.zone.doomsday.withering

import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.world.mana.ManaField
import org.springframework.stereotype.Component
import kotlin.math.hypot

/** How corruption creeps out from crystals into the [CorruptionField]: strongest at a crystal's foot. */
@Component
class CorruptionSpread(
  private val config: WitheringConfig,
  private val field: CorruptionField,
) {

  /**
   * Lets the land under [crystals] rot for [days], except the [warded] cells. With [heal], the land no crystal reaches
   * any more heals.
   */
  fun corrupt(crystals: List<Vec2d>, days: Double, warded: Set<Long>, heal: Boolean) {
    synchronized(field) {
      val reached = HashMap<ManaField.Cell, Double>()
      for (crystal in crystals) {
        cellsWithin(crystal.x, crystal.y, config.crystalReachMetres).forEach { cell ->
          val nearness = 1.0 - hypot(cell.centreX - crystal.x, cell.centreY - crystal.y) / config.crystalReachMetres
          reached.merge(cell, nearness, ::maxOf)
        }
      }

      reached
        .filterKeys { it.key !in warded }
        .forEach { (cell, nearness) -> field.change(cell, config.corruptPerDay * days * nearness) }
      if (heal) {
        field.corrupted()
          .filter { (cell, _) -> cell !in reached }
          .forEach { (cell, _) -> field.change(cell, -config.healPerDay * days) }
      }
      field.dropHealed()
    }
  }

  companion object {
    /** The cells whose centre lies within [radius] metres of [x], [y]. */
    fun cellsWithin(x: Double, y: Double, radius: Double): List<ManaField.Cell> {
      val reach = (radius / ManaField.CELL_METRES).toInt() + 1
      val centreX = ManaField.cellOf(x)
      val centreY = ManaField.cellOf(y)
      return (centreY - reach..centreY + reach).flatMap { cy ->
        (centreX - reach..centreX + reach).map { cx -> ManaField.Cell(cx, cy) }
      }.filter { hypot(it.centreX - x, it.centreY - y) <= radius }
    }
  }
}
