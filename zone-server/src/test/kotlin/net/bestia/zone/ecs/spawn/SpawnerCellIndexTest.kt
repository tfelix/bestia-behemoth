package net.bestia.zone.ecs.spawn

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpawnerCellIndexTest {

  private val index = SpawnerCellIndex()

  private fun near(x: Long, y: Long, z: Long = 0): Set<EntityId> =
    HashSet<EntityId>().also { index.collectNear(Vec3L(x, y, z), it) }

  private val cell = SpawnerCellIndex.CELL_SIZE

  @Test
  fun `a den is found from its own cell and from all eight neighbours`() {
    // Placed well inside a cell, so the eight query points below are each squarely in a different neighbour
    // rather than sitting on a boundary - the boundaries themselves are the previous test's business.
    val den = 1L
    index.add(den, Vec3L(cell + cell / 2, cell + cell / 2, 0))

    for (dx in -1..1) {
      for (dy in -1..1) {
        val x = cell + cell / 2 + dx * cell
        val y = cell + cell / 2 + dy * cell
        assertTrue(den in near(x, y), "den not found from offset ($dx, $dy)")
      }
    }
  }

  @Test
  fun `a den two cells away is not returned`() {
    index.add(1L, Vec3L(0, 0, 0))

    assertEquals(emptySet(), near(2 * cell, 0))
    assertEquals(emptySet(), near(0, 2 * cell))
  }

  @Test
  fun `negative coordinates bucket the same way positive ones do`() {
    // The regression test for `shr` versus `/`. Integer division truncates towards zero, which would put -1
    // and +1 in the same cell and make that one cell twice as wide as every other. The world is centred on
    // the origin, so a den just west of the axis would then be found from a query two cells away - and never
    // found from the cell it is actually in. Mirrored placements must behave identically.
    val west = 1L
    val east = 2L
    index.add(west, Vec3L(-3 * cell - cell / 2, 0, 0))
    index.add(east, Vec3L(3 * cell + cell / 2, 0, 0))

    assertTrue(west in near(-3 * cell - cell / 2, 0), "den at negative x not found in its own cell")
    assertTrue(east in near(3 * cell + cell / 2, 0), "den at positive x not found in its own cell")

    // And each is invisible from two cells away, on the side that a truncating division would have widened.
    assertTrue(west !in near(-cell - cell / 2, 0), "negative-x cells are wider than positive ones")
    assertTrue(east !in near(cell + cell / 2, 0))
  }

  @Test
  fun `a den on a cell boundary is found from both sides of it`() {
    val den = 1L
    index.add(den, Vec3L(cell, cell, 0))

    assertTrue(den in near(cell - 1, cell - 1))
    assertTrue(den in near(cell, cell))
  }

  @Test
  fun `several dens in one cell are all returned, and counted`() {
    index.add(1L, Vec3L(10, 10, 0))
    index.add(2L, Vec3L(20, 20, 0))
    index.add(3L, Vec3L(30, 30, 500))

    assertEquals(setOf(1L, 2L, 3L), near(15, 15))
    assertEquals(3, index.size)
  }

  @Test
  fun `height is not part of the key`() {
    // Activation is horizontal on purpose - see SpawnerSystem.withinActivation - so a den far below a player
    // is still its business.
    index.add(1L, Vec3L(0, 0, -5_000))

    assertTrue(1L in near(0, 0, 5_000))
  }
}
