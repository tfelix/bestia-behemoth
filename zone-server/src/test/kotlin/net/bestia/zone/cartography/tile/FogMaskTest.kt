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
    assertEquals(FogMask.FALLOFF_METRES, tile.bounds.minX - clearing.minX)
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
