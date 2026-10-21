package net.bestia.zone.cartography.tile

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.core.WorldWrap
import net.bestia.zone.cartography.coverage.Coverage
import net.bestia.zone.cartography.coverage.SurveyGrid
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FogMaskTest {

  private val grid = SurveyGrid(WorldWrap(WorldConfig(seed = 1L, widthCells = 128, heightCells = 128)))

  @Test
  fun `a tile well inside a chart is fully clear`() {
    // The halo is what makes this pass. Without it the distance field would see no coverage beyond the tile, put
    // its edge cells one step from unknown ground, and ramp the fringe down along every tile boundary - the fog
    // would draw the tile grid over ground the player has charted.
    val coverage = Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 10_000.0) }
    // Level 4, so the tile is 4 km across and sits inside the disc. A level-6 tile is 16 km across - wider than
    // this survey - and would be partly uncharted however far inside its centre lay.
    val tile = TileId.of(4, 64_000.0, 64_000.0)

    val mask = FogMask.forTile(tile, coverage)

    assertTrue(mask.isFullyClear, "a tile 10 km inside a chart is being masked")
    assertFalse(mask.isFullyHidden)
    assertTrue(alphaOf(mask).all { it == 255 }, "some pixel of a fully charted tile is not opaque")
  }

  @Test
  fun `a tile with no coverage is fully hidden`() {
    val coverage = Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 2_000.0) }
    val tile = TileId.of(6, 20_000.0, 20_000.0)

    val mask = FogMask.forTile(tile, coverage)

    assertTrue(mask.isFullyHidden)
    assertFalse(mask.isFullyClear)
    assertTrue(alphaOf(mask).all { it == 0 })
  }

  @Test
  fun `alpha zero means colour zero`() {
    // PNG stores the colour of a transparent pixel like any other, so alpha alone conceals nothing. Anyone who
    // reads the file rather than displaying it would see every uncharted pixel of the map.
    val coverage = Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 1_500.0) }
    val tile = TileId.of(4, 64_000.0, 64_000.0)
    val masked = FogMask.forTile(tile, coverage).applyTo(filled(0xFFCC66))

    var transparent = 0
    for (py in 0 until masked.height) {
      for (px in 0 until masked.width) {
        val argb = masked.getRGB(px, py)
        if (argb ushr 24 != 0) continue

        transparent++
        assertEquals(0, argb and 0xFFFFFF, "a transparent pixel at $px,$py still carries its colour")
      }
    }

    assertTrue(transparent > 0, "the tile chosen has no hidden pixels, so the test proves nothing")
  }

  @Test
  fun `the fringe fades inward and never outward`() {
    // Both halves matter. Monotonic, so the edge reads as a fade rather than as noise; and zero outside the
    // chart, because a fringe that ramped outward would disclose a strip of unknown ground round every chart.
    val coverage = Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 4_000.0) }
    val tile = TileId.of(2, 68_000.0, 64_000.0)
    val mask = FogMask.forTile(tile, coverage)
    val masked = mask.applyTo(filled(0xFFFFFF))

    // The disc's eastern edge crosses this row. Walking east, alpha must never rise.
    val row = ((tile.bounds.maxY - 64_000.0) / tile.metresPerPixel).toInt()
    var previous = 255
    for (px in 0 until masked.width) {
      val alpha = masked.getRGB(px, row) ushr 24

      assertTrue(alpha <= previous, "alpha rose from $previous to $alpha at column $px, going away from the chart")
      previous = alpha
    }

    val outside = 64_000.0 + 4_100.0
    assertEquals(0, alphaAt(masked, tile, outside, 64_000.0), "ground beyond the chart is partly revealed")
  }

  @Test
  fun `the mask is the same field at every level up to the lattice step`() {
    // The strong form of the seam property. At and below 64 m per pixel the lattice *is* the survey grid, so the
    // reveal is one function of world position and two different levels must agree on it. If it were derived from
    // the tile frame instead, L0 and L6 would disagree everywhere.
    val coverage = Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 3_000.0) }

    for (offset in intArrayOf(2_880, 2_944, 3_008, 3_072)) {
      val worldX = 64_000.0 + offset + SurveyGrid.CELL_METRES / 2
      val worldY = 64_000.0 + SurveyGrid.CELL_METRES / 2

      val coarse = alphaOfPoint(coverage, 6, worldX, worldY)
      val fine = alphaOfPoint(coverage, 0, worldX, worldY)

      // One quantisation step of slack: a level-0 pixel is 1 m wide, so its centre sits half a metre off the
      // lattice centre the level-6 pixel lands on exactly.
      assertTrue(
        abs(coarse - fine) <= 255 / (FogMask.ALPHA_LEVELS - 1),
        "L6 gave $coarse and L0 gave $fine at $worldX, $worldY"
      )
    }
  }

  @Test
  fun `two charts meeting leave no fringe between them`() {
    // A merged chart is several discs. The fringe belongs to the boundary of the union, so a seam of dimmed
    // pixels along where two discs touch would mean the distance field was being computed per disc.
    val single = Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 2_000.0) }
    val pair = single.copy().apply { fillDisc(67_000.0, 64_000.0, 2_000.0) }

    val overlap = 65_950.0
    assertTrue(alphaOfPoint(single, 2, overlap, 64_000.0) < 255, "the seed disc alone should dim this point")
    assertEquals(255, alphaOfPoint(pair, 2, overlap, 64_000.0), "the union should reveal it fully")
  }

  @Test
  fun `the clearing area is wider than the tile`() {
    // Coverage that stops on a tile edge still fringes inside that tile, so asking only about the tile's own
    // bounds would serve it unmasked against a neighbour that shows a fade - a seam.
    val tile = TileId.of(5, 64_000.0, 64_000.0)
    val clearing = FogMask.clearingArea(tile)

    assertTrue(clearing.minX < tile.bounds.minX && clearing.maxX > tile.bounds.maxX)
    assertEquals(FogMask.REACH_METRES, tile.bounds.minX - clearing.minX)
  }

  @Test
  fun `the edge follows the chart and not the survey grid`() {
    // A disc drawn on 64 m cells is a staircase. A fade that is only interpolated between lattice values keeps
    // that staircase as a ripple round the whole edge, which is what close zoom showed. Smoothed, the alpha on a
    // circle a fixed distance inside the edge is much more nearly one value all the way round.
    val coverage = Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 3_000.0) }
    val alphas = (0 until 120).map { step ->
      val angle = 0.05 + step * (Math.PI / 2 - 0.1) / 119
      alphaOfPoint(coverage, 1, 64_000.0 + 2_900.0 * Math.cos(angle), 64_000.0 + 2_900.0 * Math.sin(angle))
    }

    // The lattice fade it replaced spreads by about 68 at this depth; this one by about 36.
    val spread = alphas.max() - alphas.min()
    assertTrue(spread <= 50, "alpha varies by $spread round a circle of constant depth: the grid is showing")
  }

  @Test
  fun `a pixel in an uncharted cell is never revealed`() {
    // Smoothing alone puts a faint alpha across the corners of a staircase. The fringe must stay inside the
    // chart, so a pixel whose own cell is not charted is zero however much of its neighbourhood is.
    val coverage = Coverage(grid).apply {
      fillDisc(64_000.0, 64_000.0, 2_000.0)
      fillDisc(66_500.0, 64_700.0, 1_200.0)
      fillDisc(63_000.0, 61_900.0, 900.0)
    }
    val tile = TileId.of(2, 66_000.0, 63_000.0)
    val masked = FogMask.forTile(tile, coverage).applyTo(filled(0xFFFFFF))

    var uncharted = 0
    var revealed = 0
    for (py in 0 until masked.height) {
      for (px in 0 until masked.width) {
        val worldX = tile.bounds.minX + (px + 0.5) * tile.metresPerPixel
        val worldY = tile.bounds.maxY - (py + 0.5) * tile.metresPerPixel
        val alpha = masked.getRGB(px, py) ushr 24

        if (coverage.contains(worldX, worldY)) {
          if (alpha > 0) revealed++
        } else {
          uncharted++
          assertEquals(0, alpha, "an uncharted pixel at $worldX, $worldY is partly revealed")
        }
      }
    }

    assertTrue(uncharted > 0 && revealed > 0, "the tile chosen does not cross a chart's edge, so the test proves nothing")
  }

  @Test
  fun `the fade is not banded where it spans many pixels`() {
    // 160 m of fade at 2 m per pixel is 80 pixels; sixteen alpha values over that show as stripes.
    val coverage = Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 3_000.0) }
    // 64 100 and not 64 000, which is the boundary between two L1 tiles and so a row this one does not have.
    val tile = TileId.of(1, 67_000.0, 64_100.0)
    val masked = FogMask.forTile(tile, coverage).applyTo(filled(0xFFFFFF))

    val row = ((tile.bounds.maxY - 64_100.0) / tile.metresPerPixel).toInt()
    val distinct = (0 until masked.width).map { masked.getRGB(it, row) ushr 24 }.toSet().size

    assertTrue(distinct > FogMask.ALPHA_LEVELS, "only $distinct alpha values across the fade")
  }

  @Test
  fun `the fine and the coarse regime agree where they meet`() {
    // L6 is the last level drawn from smoothed cells and L7 the first drawn from the lattice. A tile at each, over
    // the same charted ground, must not put the edge in different places by more than a pixel of the coarser.
    val coverage = Coverage(grid).apply { fillDisc(64_000.0, 64_000.0, 30_000.0) }
    val edge = 64_000.0 + 30_000.0

    val fine = revealedUntil(coverage, 6, edge)
    val coarse = revealedUntil(coverage, 7, edge)

    assertTrue(abs(fine - coarse) <= 2 * 128.0, "the edge is at $fine on L6 and $coarse on L7")
  }

  /** The easternmost world x at which a tile's row through the chart's centre is still more than half revealed. */
  private fun revealedUntil(coverage: Coverage, level: Int, near: Double): Double {
    val tile = TileId.of(level, near, 64_000.0)
    val masked = FogMask.forTile(tile, coverage).applyTo(filled(0xFFFFFF))
    val row = ((tile.bounds.maxY - 64_000.0) / tile.metresPerPixel).toInt()

    var last = tile.bounds.minX
    for (px in 0 until masked.width) {
      if (masked.getRGB(px, row) ushr 24 > 127) last = tile.bounds.minX + (px + 0.5) * tile.metresPerPixel
    }
    return last
  }

  private fun alphaOfPoint(coverage: Coverage, level: Int, worldX: Double, worldY: Double): Int {
    val tile = TileId.of(level, worldX, worldY)
    val masked = FogMask.forTile(tile, coverage).applyTo(filled(0xFFFFFF))
    return alphaAt(masked, tile, worldX, worldY)
  }

  private fun alphaAt(masked: BufferedImage, tile: TileId, worldX: Double, worldY: Double): Int {
    val px = ((worldX - tile.bounds.minX) / tile.metresPerPixel).toInt()
    val py = ((tile.bounds.maxY - worldY) / tile.metresPerPixel).toInt()
    return masked.getRGB(px, py) ushr 24
  }

  private fun alphaOf(mask: FogMask): List<Int> {
    val masked = mask.applyTo(filled(0xFFFFFF))
    return (0 until masked.height).flatMap { py ->
      (0 until masked.width).map { px -> masked.getRGB(px, py) ushr 24 }
    }
  }

  private fun filled(rgb: Int): BufferedImage {
    val image = BufferedImage(TileId.TILE_PIXELS, TileId.TILE_PIXELS, BufferedImage.TYPE_INT_RGB)
    for (py in 0 until image.height) {
      for (px in 0 until image.width) image.setRGB(px, py, rgb)
    }
    return image
  }
}
