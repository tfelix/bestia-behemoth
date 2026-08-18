package net.bestia.zone.cartography.coverage

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.core.WorldWrap
import net.bestia.worldgen.vector.Aabb
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverageCodecTest {

  private val grid = SurveyGrid(WorldWrap(world(cells = 128)))
  private val wholeWorld = Aabb(0.0, 0.0, 128_000.0, 128_000.0)

  @Test
  fun `an empty coverage round trips`() {
    val decoded = CoverageCodec.decode(CoverageCodec.encode(Coverage(grid)), grid)

    assertTrue(decoded.isEmpty)
    assertEquals(0L, decoded.cellCount())
  }

  @Test
  fun `one survey round trips exactly`() {
    val coverage = Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 5_000.0) }
    val decoded = CoverageCodec.decode(CoverageCodec.encode(coverage), grid)

    assertEquals(coverage.cellCount(), decoded.cellCount())
    assertEquals(coverage.bounds(), decoded.bounds())
    assertEquals(coverage.coverageOf(wholeWorld), decoded.coverageOf(wholeWorld))
  }

  @Test
  fun `a merged chart of scattered surveys round trips`() {
    // Several blocks, non-adjacent, so the delta-coded keys take their large jumps as well as their small ones.
    val coverage = Coverage(grid)
    coverage.fillDisc(10_000.0, 10_000.0, 2_000.0)
    coverage.fillDisc(100_000.0, 20_000.0, 3_500.0)
    coverage.fillDisc(60_000.0, 110_000.0, 1_000.0)
    coverage.fillDisc(127_800.0, 64_000.0, 2_000.0)

    val decoded = CoverageCodec.decode(CoverageCodec.encode(coverage), grid)

    assertEquals(coverage.cellCount(), decoded.cellCount())
    assertEquals(coverage.coverageOf(wholeWorld), decoded.coverageOf(wholeWorld))
    for (x in intArrayOf(10_000, 100_000, 60_000, 300)) {
      for (y in intArrayOf(10_000, 20_000, 110_000, 64_000)) {
        assertEquals(
          coverage.contains(x.toDouble(), y.toDouble()),
          decoded.contains(x.toDouble(), y.toDouble()),
          "disagreement at $x, $y"
        )
      }
    }
  }

  @Test
  fun `a survey costs a few hundred bytes rather than the raw blocks`() {
    // Recorded rather than asserted loosely: this bound is the reason a chart is a column on a row and not a file.
    val coverage = Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 5_000.0) }
    val encoded = CoverageCodec.encode(coverage)

    val cells = coverage.cellCount()
    assertTrue(cells > 19_000, "a 5 km survey should be about 19 200 cells, was $cells")
    assertTrue(encoded.size < 2_000, "encoded to ${encoded.size} bytes, expected well under 2 kB")
  }

  @Test
  fun `a fully charted world stays small because a solid bitset is a run`() {
    val coverage = Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 91_000.0) }
    val encoded = CoverageCodec.encode(coverage)

    assertEquals(grid.cellsAcross.toLong() * grid.cellsDown, coverage.cellCount())
    assertTrue(
      encoded.size < 100_000,
      "a charted 128 km world is 500 kB of raw blocks; encoded to ${encoded.size}, expected under 100 kB"
    )
    assertEquals(coverage.cellCount(), CoverageCodec.decode(encoded, grid).cellCount())
  }

  @Test
  fun `the version is the first byte`() {
    assertEquals(CoverageCodec.VERSION, CoverageCodec.encode(Coverage(grid))[0].toInt())
  }

  @Test
  fun `a blob written for another world is detected rather than misread`() {
    // The bits are positions. Read against a differently sized grid they would place their owner somewhere they
    // have never been, and nothing in the bits themselves would give that away.
    val other = SurveyGrid(WorldWrap(world(cells = 256)))
    val encoded = CoverageCodec.encode(Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 3_000.0) })

    assertTrue(CoverageCodec.isReadableBy(encoded, grid))
    assertFalse(CoverageCodec.isReadableBy(encoded, other))
    assertFailsWith<IllegalArgumentException> { CoverageCodec.decode(encoded, other) }
  }

  @Test
  fun `a truncated or foreign blob is refused`() {
    val encoded = CoverageCodec.encode(Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 3_000.0) })

    assertFalse(CoverageCodec.isReadableBy(ByteArray(0), grid))
    assertFalse(CoverageCodec.isReadableBy(byteArrayOf(99, 64, 1, 1), grid))
    assertFailsWith<IllegalArgumentException> { CoverageCodec.decode(ByteArray(2), grid) }
    assertFailsWith<Exception> { CoverageCodec.decode(encoded.copyOf(encoded.size / 2), grid) }
  }

  private fun world(cells: Int) = WorldConfig(seed = 1L, widthCells = cells, heightCells = cells)
}
