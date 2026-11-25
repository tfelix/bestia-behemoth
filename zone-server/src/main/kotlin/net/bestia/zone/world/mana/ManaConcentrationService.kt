package net.bestia.zone.world.mana

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField.Cell
import org.springframework.stereotype.Service

/**
 * The mana where something stands. Density is one value per field cell, so what a GM sets is what a spell
 * reads anywhere in that cell.
 */
@Service
class ManaConcentrationService(
  private val worldService: WorldService,
  private val field: ManaField,
  private val ground: ManaGround,
) {

  /** Where the rock holds mana: the worldgen rank, 0 on a world without the mana stage. For geology, like herbs. */
  fun potentialAt(at: Vec3L): Double {
    return worldService.generated.materializer.surface.potentialAt(at.x.toDouble(), at.y.toDouble())
  }

  /** How much mana is in the air: what magic, weather and omens react to. */
  fun densityAt(at: Vec3L): Double = densityOf(cellAt(at))

  fun densityOf(cell: Cell): Double = (ground.startDensity(cell) + field.deltaOf(cell.x, cell.y)).coerceAtLeast(0.0)

  fun placeAt(at: Vec3L): Int = ManaConcentration.place(densityAt(at))

  fun factorAt(at: Vec3L): Double = ManaConcentration.factor(densityAt(at))

  /** Releases [motes] at [at], spread over [radius] metres. Negative motes take mana away. */
  fun release(at: Vec3L, motes: Double, radius: Double = 0.0) {
    field.add(at.x.toDouble(), at.y.toDouble(), motes, radius)
  }

  /** Sets every cell within [radius] metres to [density], whatever the land holds there. */
  fun setDensity(at: Vec3L, density: Double, radius: Double = 0.0) {
    field.cellsWithin(at.x.toDouble(), at.y.toDouble(), radius).forEach { cell ->
      field.setDelta(cell, density - ground.startDensity(cell))
    }
  }

  fun cellAt(at: Vec3L): Cell = Cell(ManaField.cellOf(at.x.toDouble()), ManaField.cellOf(at.y.toDouble()))
}
