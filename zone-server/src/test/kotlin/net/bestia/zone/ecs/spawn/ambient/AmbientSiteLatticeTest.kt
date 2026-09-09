package net.bestia.zone.ecs.spawn.ambient

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The geometry the whole ambient layer rests on.
 *
 * Determinism is the load-bearing property: it is what makes walking away and back leave the country
 * undisturbed, and what makes every observer of the same tile see the same creature. It is also the property
 * that would fail *quietly* - a wilderness that reshuffles looks like a spawning bug anywhere else.
 */
class AmbientSiteLatticeTest {

  private val spacing = 30L
  private val sut = AmbientSiteLattice(SEED, spacing)

  @Test
  fun `a cell offers the same site however often it is asked`() {
    for (cellX in -3L..3L) {
      for (cellY in -3L..3L) {
        assertEquals(sut.siteX(cellX, cellY), sut.siteX(cellX, cellY))
        assertEquals(sut.siteY(cellX, cellY), sut.siteY(cellX, cellY))
        assertEquals(sut.siteId(cellX, cellY), sut.siteId(cellX, cellY))
      }
    }
  }

  @Test
  fun `a fresh lattice on the same seed agrees with the old one`() {
    val other = AmbientSiteLattice(SEED, spacing)

    for (cellX in -50L..50L) {
      assertEquals(sut.siteX(cellX, 7), other.siteX(cellX, 7))
      assertEquals(sut.siteY(cellX, 7), other.siteY(cellX, 7))
      assertEquals(sut.thinningRoll(cellX, 7), other.thinningRoll(cellX, 7))
    }
  }

  @Test
  fun `another seed puts the sites somewhere else`() {
    val other = AmbientSiteLattice(SEED + 1, spacing)

    val moved = (-200L..200L).count { sut.siteX(it, 3) != other.siteX(it, 3) }

    assertTrue(moved > 300, "only $moved of 401 sites moved when the seed changed")
  }

  /**
   * Every site lies inside the cell that offers it.
   *
   * Which is what makes coverage even - the point of a jittered grid over the Poisson process the dens use -
   * and what lets the search widen by exactly one cell.
   */
  @Test
  fun `a site lies inside its own cell`() {
    for (cellX in -100L..100L) {
      for (cellY in -3L..3L) {
        val x = sut.siteX(cellX, cellY)
        val y = sut.siteY(cellX, cellY)

        assertTrue(x >= cellX * spacing && x < (cellX + 1) * spacing, "site x $x outside cell $cellX")
        assertTrue(y >= cellY * spacing && y < (cellY + 1) * spacing, "site y $y outside cell $cellY")
        assertEquals(cellX, sut.cellXOf(x))
        assertEquals(cellY, sut.cellYOf(y))
      }
    }
  }

  /**
   * The trap `SpawnerCellIndex` documents, on a lattice that cannot use `shr` because the spacing is not a
   * power of two.
   *
   * Plain integer division truncates towards zero, so cell 0 would be 59 tiles wide instead of 30 and the
   * whole lattice would be mirrored across each axis. Half of an origin-centred world is negative, so this
   * is not an edge case - it is half the map.
   */
  @Test
  fun `cells are the same width either side of the origin`() {
    assertEquals(-1L, sut.cellXOf(-1))
    assertEquals(-1L, sut.cellXOf(-spacing))
    assertEquals(-2L, sut.cellXOf(-spacing - 1))
    assertEquals(0L, sut.cellXOf(0))
    assertEquals(0L, sut.cellXOf(spacing - 1))
    assertEquals(1L, sut.cellXOf(spacing))

    // Every cell holds exactly `spacing` tiles, negatives included.
    for (cell in -5L..5L) {
      val tiles = (cell * spacing until (cell + 1) * spacing).count { sut.cellXOf(it) == cell }
      assertEquals(spacing.toInt(), tiles, "cell $cell is the wrong width")
    }
  }

  @Test
  fun `packing round-trips negative cells`() {
    for (x in listOf(-1L, -70_000L, 0L, 1L, 70_000L, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())) {
      for (y in listOf(-1L, -70_000L, 0L, 1L, 70_000L)) {
        val cell = AmbientSiteLattice.pack(x, y)
        assertEquals(x, AmbientSiteLattice.unpackX(cell), "x of ($x,$y)")
        assertEquals(y, AmbientSiteLattice.unpackY(cell), "y of ($x,$y)")
      }
    }
  }

  @Test
  fun `separate salts keep jitter and thinning uncorrelated`() {
    // Same cell, so a shared stream would make these three equal or trivially related.
    val x = sut.siteX(4, 9)
    val y = sut.siteY(4, 9)
    val roll = sut.thinningRoll(4, 9)

    assertNotEquals(x - 4 * spacing, y - 9 * spacing)
    assertTrue(roll in 0.0..1.0)
  }

  /** The search has to offer every cell whose site could be in range, including the widened border. */
  @Test
  fun `forEachCellNear covers every cell whose site is within the radius`() {
    val radius = 100L
    val visited = HashSet<Long>()
    sut.forEachCellNear(0, 0, radius) { cx, cy -> visited.add(AmbientSiteLattice.pack(cx, cy)) }

    for (cellX in -10L..10L) {
      for (cellY in -10L..10L) {
        val x = sut.siteX(cellX, cellY)
        val y = sut.siteY(cellX, cellY)
        if (x * x + y * y > radius * radius) continue

        assertTrue(
          AmbientSiteLattice.pack(cellX, cellY) in visited,
          "cell ($cellX,$cellY) holds a site at ($x,$y) inside the radius but was not offered"
        )
      }
    }
  }

  private companion object {
    /** `application.yml`'s pinned dev seed, so this measures the lattice the dev server actually uses. */
    const val SEED = 11_753_242L
  }
}
