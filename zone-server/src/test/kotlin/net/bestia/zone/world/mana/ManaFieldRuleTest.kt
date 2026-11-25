package net.bestia.zone.world.mana

import net.bestia.zone.world.mana.ManaField.Cell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class ManaFieldRuleTest {

  private val field = ManaField()

  @Test
  fun `half of the added mana is gone after a month`() {
    field.add(MIDDLE, MIDDLE, motes = 1000.0)

    run(ManaFieldRule(ManaFieldConfig(), BESTIA_DAY), days = 30.0, stepSeconds = 600.0)

    assertEquals(500.0, totalMotes(), 5.0)
  }

  @Test
  fun `mana creeps a few hundred metres in a week`() {
    field.add(MIDDLE, MIDDLE, motes = 1000.0)

    run(ManaFieldRule(ManaFieldConfig(), BESTIA_DAY), days = 7.0, stepSeconds = 600.0)

    // Spread in two dimensions is sqrt(2) times the 300 m along one axis.
    assertEquals(424.0, spreadMetres(), 40.0)
  }

  @Test
  fun `a field nobody touches returns to the start density`() {
    field.add(MIDDLE, MIDDLE, motes = 100.0, radius = 500.0)

    run(ManaFieldRule(ManaFieldConfig(), BESTIA_DAY), days = 365.0, stepSeconds = 3600.0)

    assertEquals(0, field.tileCount)
  }

  @Test
  fun `start towns drain added mana halfway to the target in three Bestia days`() {
    val rule = ManaFieldRule(ManaFieldConfig(creepMetresPerWeek = 0.0), BESTIA_DAY)
    field.setDelta(TOWN, 0.7)
    field.setDelta(WILD, 0.7)

    run(rule, days = 3 * BESTIA_DAY / DAY, stepSeconds = 60.0)

    assertEquals(0.3 + 0.25, LOW_LAND + field.deltaOf(TOWN.x, TOWN.y), 0.02)
    assertEquals(0.8, LOW_LAND + field.deltaOf(WILD.x, WILD.y), 0.02)
  }

  @Test
  fun `start towns never drain below what the land holds`() {
    val richTown = object : ManaFieldRule.Ground {
      override fun startDensity(cell: Cell) = 0.5
      override fun isSanctuary(cell: Cell) = true
    }
    val rule = ManaFieldRule(ManaFieldConfig(creepMetresPerWeek = 0.0), BESTIA_DAY)
    field.setDelta(TOWN, 0.2)

    repeat(30 * 24) { rule.step(field, richTown, 3600.0) }

    assertEquals(0.0, field.deltaOf(TOWN.x, TOWN.y), 0.02)
    assertTrue(field.deltaOf(TOWN.x, TOWN.y) >= 0.0)
  }

  private fun run(rule: ManaFieldRule, days: Double, stepSeconds: Double) {
    repeat((days * DAY / stepSeconds).toInt()) { rule.step(field, ground, stepSeconds) }
  }

  private fun totalMotes(): Double =
    field.tileKeys().sumOf { field.tileArray(it)!!.sum() } / ManaField.DELTA_PER_MOTE

  private fun spreadMetres(): Double {
    var weight = 0.0
    var squares = 0.0
    field.tileKeys().forEach { key ->
      field.tileArray(key)!!.forEachIndexed { index, delta ->
        val cell = Cell(
          ManaField.tileXOf(key) * ManaField.TILE_CELLS + index % ManaField.TILE_CELLS,
          ManaField.tileYOf(key) * ManaField.TILE_CELLS + index / ManaField.TILE_CELLS,
        )
        val dx = cell.centreX - MIDDLE_CENTRE
        val dy = cell.centreY - MIDDLE_CENTRE
        weight += delta
        squares += delta * (dx * dx + dy * dy)
      }
    }
    return sqrt(squares / weight)
  }

  /** Low land everywhere; only the cell [TOWN] is a start town. */
  private val ground = object : ManaFieldRule.Ground {
    override fun startDensity(cell: Cell) = LOW_LAND
    override fun isSanctuary(cell: Cell) = cell == TOWN
  }

  private companion object {
    const val DAY = 86_400.0
    const val BESTIA_DAY = DAY / 3

    const val LOW_LAND = 0.1

    /** Inside a tile, so the spread does not start on a tile edge. */
    const val MIDDLE = 2050.0
    val MIDDLE_CENTRE = ManaField.cellOf(MIDDLE) * ManaField.CELL_METRES + ManaField.CELL_METRES / 2

    val TOWN = Cell(5, 5)
    val WILD = Cell(20, 20)
  }
}
