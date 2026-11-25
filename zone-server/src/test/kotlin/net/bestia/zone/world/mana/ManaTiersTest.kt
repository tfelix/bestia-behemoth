package net.bestia.zone.world.mana

import net.bestia.zone.world.mana.ManaField.Cell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManaTiersTest {

  private val field = ManaField()
  private val ground = object : ManaFieldRule.Ground {
    override fun startDensity(cell: Cell) = START
    override fun isSanctuary(cell: Cell) = false
  }
  private val heard = ArrayList<Triple<Cell, ManaTier, ManaTier>>()
  private val tiers = ManaTiers(field, ground, listOf(ManaTierListener { cell, from, to -> heard += Triple(cell, from, to) }))
  private val rule = ManaTierRule(dwellSeconds = DWELL)

  @Test
  fun `a density is cut into the tiers of the ladder`() {
    assertEquals(ManaTier.CALM, ManaTier.of(0.13))
    assertEquals(ManaTier.UNEASY, ManaTier.of(0.45))
    assertEquals(ManaTier.STORMS, ManaTier.of(0.64))
    assertEquals(ManaTier.BURN, ManaTier.of(2.0))
  }

  @Test
  fun `a place rises only after it stayed past the border for the dwell time`() {
    tiers.update(rule, 60.0)
    densityTo(0.50)

    tiers.update(rule, 60.0)
    tiers.update(rule, DWELL - 120.0)
    assertEquals(ManaTier.CALM, tiers.tierOf(CELL))

    tiers.update(rule, 120.0)
    assertEquals(ManaTier.UNEASY, tiers.tierOf(CELL))
    assertEquals(listOf(Triple(CELL, ManaTier.CALM, ManaTier.UNEASY)), heard)
  }

  @Test
  fun `a place falls only once it is well below the border`() {
    tiers.update(rule, 60.0)
    densityTo(0.50)
    tiers.update(rule, 60.0)
    tiers.update(rule, DWELL)

    densityTo(0.43)
    tiers.update(rule, DWELL * 2)
    assertEquals(ManaTier.UNEASY, tiers.tierOf(CELL))

    densityTo(0.40)
    tiers.update(rule, 60.0)
    tiers.update(rule, DWELL)
    assertEquals(ManaTier.STIRRING, tiers.tierOf(CELL))
  }

  @Test
  fun `after a restart the saved field has its tier at once and tells nobody`() {
    densityTo(0.62)

    tiers.update(rule, 60.0)

    assertEquals(ManaTier.STORMS, tiers.tierOf(CELL))
    assertEquals(emptyList<Any>(), heard)
  }

  @Test
  fun `a place nobody touched sits at the tier of its start density`() {
    assertEquals(ManaTier.CALM, tiers.tierOf(Cell(100, 100)))
  }

  private fun densityTo(density: Double) {
    field.setDelta(CELL, density - START)
  }

  private companion object {
    const val START = 0.13
    const val DWELL = 1200.0
    val CELL = Cell(4, 4)
  }
}
