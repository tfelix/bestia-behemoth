package net.bestia.zone.world.mana

import net.bestia.zone.world.mana.ManaField.Cell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManaFieldTest {

  private val field = ManaField()

  @Test
  fun `one mote raises one cell by a thousandth`() {
    field.add(10.0, 10.0, motes = 1.0)

    assertEquals(0.001, field.deltaAt(100.0, 100.0), 1e-12)
    assertEquals(0.0, field.deltaAt(200.0, 100.0))
  }

  @Test
  fun `motes spread over a radius keep their total`() {
    field.add(1000.0, 1000.0, motes = 100.0, radius = 500.0)

    val cells = field.cellsWithin(1000.0, 1000.0, 500.0)
    val total = cells.sumOf { field.deltaOf(it.x, it.y) } / ManaField.DELTA_PER_MOTE

    assertEquals(100.0, total, 1e-9)
    assertEquals(1, field.tileCount)
  }

  @Test
  fun `cells west and south of the origin land in their own tiles`() {
    field.setDelta(Cell(-1, -1), 0.5)
    field.setDelta(Cell(0, 0), 0.25)

    assertEquals(0.5, field.deltaAt(-1.0, -1.0))
    assertEquals(0.25, field.deltaAt(1.0, 1.0))
    assertEquals(2, field.tileCount)
  }

  @Test
  fun `changes are handed out once`() {
    field.setDelta(Cell(3, 4), 0.1)

    assertEquals(1, field.takeChanges().written.size)
    assertEquals(0, field.takeChanges().written.size)
  }
}
