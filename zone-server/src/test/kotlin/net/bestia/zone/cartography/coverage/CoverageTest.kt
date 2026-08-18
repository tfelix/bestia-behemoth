package net.bestia.zone.cartography.coverage

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.core.WorldWrap
import net.bestia.worldgen.vector.Aabb
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverageTest {

  private val grid = SurveyGrid(WorldWrap(WorldConfig(seed = 1L, widthCells = 128, heightCells = 128)))

  @Test
  fun `a survey charts about the area of its disc`() {
    val coverage = Coverage(grid)
    val added = coverage.fillDisc(64_000.0, 64_000.0, 1_000.0)

    val expected = PI * 1_000.0 * 1_000.0 / (SurveyGrid.CELL_METRES * SurveyGrid.CELL_METRES)
    val error = abs(added - expected) / expected

    assertTrue(error < 0.02, "$added cells for a 1 km survey, expected about ${expected.toInt()}")
    assertEquals(added.toLong(), coverage.cellCount())
  }

  @Test
  fun `a survey charts inside its radius and not outside`() {
    val coverage = Coverage(grid)
    coverage.fillDisc(64_000.0, 64_000.0, 5_000.0)

    assertTrue(coverage.contains(64_000.0, 64_000.0))
    assertTrue(coverage.contains(64_000.0 + 4_800.0, 64_000.0))
    assertFalse(coverage.contains(64_000.0 + 5_200.0, 64_000.0))
    assertFalse(coverage.contains(20_000.0, 20_000.0))
  }

  @Test
  fun `charting the same ground twice adds nothing`() {
    val coverage = Coverage(grid)
    val first = coverage.fillDisc(64_000.0, 64_000.0, 2_000.0)

    assertEquals(0, coverage.fillDisc(64_000.0, 64_000.0, 2_000.0))
    assertEquals(first.toLong(), coverage.cellCount())
  }

  @Test
  fun `a survey beside the seam charts the ground on the far side`() {
    // Genesis wraps east into west, so a survey 500 m from the eastern edge reaches ground whose coordinates are
    // near zero. Without seam-aware distance those cells are 128 km away and the chart would stop at the edge.
    val coverage = Coverage(grid)
    coverage.fillDisc(127_500.0, 64_000.0, 2_000.0)

    assertTrue(coverage.contains(500.0, 64_000.0), "the chart stops at the world edge instead of wrapping")
    assertFalse(coverage.contains(3_000.0, 64_000.0))
  }

  @Test
  fun `a merge is the union and leaves both charts alone`() {
    val west = Coverage(grid).apply { fillDisc(40_000.0, 64_000.0, 3_000.0) }
    val east = Coverage(grid).apply { fillDisc(60_000.0, 64_000.0, 3_000.0) }
    val westCells = west.cellCount()
    val eastCells = east.cellCount()

    val merged = west.unionWith(east)

    assertEquals(westCells + eastCells, merged.cellCount(), "the discs do not overlap, so the counts add")
    assertEquals(westCells, west.cellCount(), "unionWith must not disturb its receiver")
    assertEquals(eastCells, east.cellCount(), "unionWith must not disturb its argument")

    assertTrue(merged.contains(40_000.0, 64_000.0))
    assertTrue(merged.contains(60_000.0, 64_000.0))
  }

  @Test
  fun `a merge is commutative`() {
    val a = Coverage(grid).apply { fillDisc(50_000.0, 50_000.0, 4_000.0) }
    val b = Coverage(grid).apply { fillDisc(52_000.0, 51_000.0, 4_000.0) }

    val area = Aabb(44_000.0, 44_000.0, 58_000.0, 58_000.0)
    assertEquals(a.unionWith(b).cellCount(), b.unionWith(a).cellCount())
    assertEquals(a.unionWith(b).coverageOf(area), b.unionWith(a).coverageOf(area))
  }

  @Test
  fun `an area is full inside a survey, none outside it and partial across its edge`() {
    val coverage = Coverage(grid)
    coverage.fillDisc(64_000.0, 64_000.0, 5_000.0)

    assertEquals(AreaCoverage.Full, coverage.coverageOf(Aabb(63_000.0, 63_000.0, 65_000.0, 65_000.0)))
    assertEquals(AreaCoverage.None, coverage.coverageOf(Aabb(10_000.0, 10_000.0, 12_000.0, 12_000.0)))
    assertTrue(coverage.coverageOf(Aabb(68_000.0, 63_000.0, 71_000.0, 65_000.0)) is AreaCoverage.Partial)
  }

  @Test
  fun `an empty coverage is none everywhere`() {
    val coverage = Coverage(grid)

    assertTrue(coverage.isEmpty)
    assertNull(coverage.bounds())
    assertEquals(AreaCoverage.None, coverage.coverageOf(Aabb(0.0, 0.0, 128_000.0, 128_000.0)))
  }

  @Test
  fun `the digest is stable for the same bits and changes for different ones`() {
    val area = Aabb(68_000.0, 63_000.0, 71_000.0, 65_000.0)

    val one = Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 5_000.0) }
    val two = Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 5_000.0) }

    // Two players who have charted the same ground must key the same masked tile, or the cache never shares.
    assertEquals(one.coverageOf(area), two.coverageOf(area))

    two.fillDisc(69_500.0, 64_000.0, 100.0)
    assertNotEquals(one.coverageOf(area), two.coverageOf(area))
  }

  @Test
  fun `coverage outside an area does not reach the digest`() {
    // The digest keys a tile, so it has to depend on that tile's bits alone. If it did not, every survey anywhere
    // in the world would invalidate every cached tile the player has.
    val area = Aabb(63_000.0, 63_000.0, 64_000.0, 64_000.0)

    val near = Coverage(grid).apply { fillDisc(63_500.0, 63_500.0, 300.0) }
    val alsoFar = near.copy().apply { fillDisc(20_000.0, 20_000.0, 4_000.0) }

    assertEquals(near.coverageOf(area), alsoFar.coverageOf(area))
  }

  @Test
  fun `an area is half open, so neighbouring areas do not share a cell`() {
    val coverage = Coverage(grid)
    coverage.fillDisc(grid.cellCentreX(1000), grid.cellCentreY(1000), 1.0)

    assertEquals(1L, coverage.cellCount(), "a one metre survey should chart exactly the cell it stands in")

    val cellX = grid.cellMinX(1000)
    val cellY = grid.cellMinY(1000)
    val cell = Aabb(cellX, cellY, cellX + SurveyGrid.CELL_METRES, cellY + SurveyGrid.CELL_METRES)

    assertEquals(AreaCoverage.Full, coverage.coverageOf(cell))
    assertEquals(
      AreaCoverage.None,
      coverage.coverageOf(cell.let { Aabb(it.maxX, it.minY, it.maxX + SurveyGrid.CELL_METRES, it.maxY) }),
      "the area starting where the charted cell ends must not claim it"
    )
  }

  @Test
  fun `bounds enclose the survey to within a cell`() {
    val coverage = Coverage(grid)
    coverage.fillDisc(64_000.0, 48_000.0, 3_000.0)

    val bounds = coverage.bounds()!!
    val disc = Aabb(61_000.0, 45_000.0, 67_000.0, 51_000.0)

    assertTrue(bounds.minX >= disc.minX - SurveyGrid.CELL_METRES && bounds.minX <= disc.minX + SurveyGrid.CELL_METRES)
    assertTrue(bounds.maxX >= disc.maxX - SurveyGrid.CELL_METRES && bounds.maxX <= disc.maxX + SurveyGrid.CELL_METRES)
    assertTrue(bounds.minY >= disc.minY - SurveyGrid.CELL_METRES && bounds.minY <= disc.minY + SurveyGrid.CELL_METRES)
    assertTrue(bounds.maxY >= disc.maxY - SurveyGrid.CELL_METRES && bounds.maxY <= disc.maxY + SurveyGrid.CELL_METRES)
  }

  @Test
  fun `an area wider than the world counts each cell once`() {
    // The level-9 tile of a 128 km world is 131 km across. A fold that produced overlapping ranges would make a
    // fully charted world report as partially charted, or worse, report Full when it was not.
    // Half the diagonal, so every cell centre is inside it. Seam-aware distance only ever shortens, so a disc
    // that reaches the corners of a flat world reaches everything in a wrapped one.
    val coverage = Coverage(grid)
    coverage.fillDisc(64_000.0, 64_000.0, 91_000.0)

    assertEquals(grid.cellsAcross.toLong() * grid.cellsDown, coverage.cellCount())
    assertEquals(AreaCoverage.Full, coverage.coverageOf(Aabb(-2_000.0, -2_000.0, 130_000.0, 130_000.0)))
  }
}
