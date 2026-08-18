package net.bestia.zone.cartography.render

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.render.Viewport
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The property the whole tile cache rests on: a tile must be indistinguishable from the same patch of a
 * larger render.
 *
 * Every other correctness question about a map style is a matter of taste. This one is not. If a pass depends
 * on anything about the *frame* it is drawn in - the tile's own pixel origin, the set of features that happen
 * to be in view, the order a scatter was generated in - then two adjacent tiles disagree along their shared
 * edge, and the result is a grid of seams over the whole map that no amount of styling can hide. It is also
 * the failure that is hardest to catch by looking, because a single tile always looks fine.
 *
 * So this renders one region two ways - as a whole, and as four quadrants - and demands the pixels match
 * exactly. That covers every pass at once, and it fails for the right reason: paper texture keyed to the tile
 * instead of the sheet, a distance transform whose halo is too narrow to see the shore outside its tile, a
 * glyph lattice seeded per frame, a feature query with too small a margin.
 */
class AtlasStyleSeamTest {

  @Test
  fun `the same viewport renders identically twice`() {
    val view = viewAt(CENTRE_X, CENTRE_Y, LEVEL, 96, 96)

    val first = pixelsOf(style.render(view, inputs))
    val second = pixelsOf(style.render(view, inputs))

    assertContentEqualsAt(first, second, "a second render of the same viewport")
  }

  @Test
  fun `four quadrants match the whole they tile`() {
    val whole = viewAt(CENTRE_X, CENTRE_Y, LEVEL, TILE * 2, TILE * 2)
    val wholePixels = pixelsOf(style.render(whole, inputs))

    val span = TILE * whole.metresPerPixel

    // Quadrant centres are half a tile from the whole view's centre in each direction.
    for (quadrantY in 0..1) {
      for (quadrantX in 0..1) {
        val view = viewAt(
          CENTRE_X + (quadrantX - 0.5) * span,
          CENTRE_Y + (0.5 - quadrantY) * span,
          LEVEL,
          TILE,
          TILE
        )
        val tile = pixelsOf(style.render(view, inputs))

        val offsetX = quadrantX * TILE
        val offsetY = quadrantY * TILE

        for (py in 0 until TILE) {
          for (px in 0 until TILE) {
            val expected = wholePixels[(py + offsetY) * TILE * 2 + px + offsetX]
            val actual = tile[py * TILE + px]

            assertEquals(
              expected,
              actual,
              "quadrant ($quadrantX, $quadrantY) pixel ($px, $py) differs from the whole render: " +
                  "expected ${hex(expected)}, was ${hex(actual)}"
            )
          }
        }
      }
    }
  }

  /**
   * The shore field must depend on world position and on nothing else - in particular not on the zoom it is
   * sampled through.
   *
   * A coastline that shifted between levels would mean a player watching the sea advance as they zoomed in,
   * and it is the one way a level-of-detail scheme can be wrong that no single-level test can see.
   *
   * The comparison has to be over *identical world points*, which is why every sample goes through a
   * one-pixel viewport: `worldX(0)` of a 1x1 view is exactly its centre at any scale, so the two reads are of
   * the same place rather than of two places within one coarse pixel. An earlier version of this test compared
   * a fine pixel against whichever coarse pixel contained it and allowed a two-metre tolerance, which asserted
   * nothing - at 256 metres per pixel the coarse sample sits up to 128 m away, where real ground differs by
   * far more than two metres.
   */
  @Test
  fun `the shore field does not move between zoom levels`() {
    val reference = mutableMapOf<Pair<Double, Double>, Double>()

    for (step in 0 until CROSS_ZOOM_SAMPLES) {
      // A diagonal walk across the coast, so the run covers deep water, the shore itself and inland.
      val worldX = CENTRE_X - 3000.0 + step * 60.0
      val worldY = CENTRE_Y - 1500.0 + step * 30.0
      reference[worldX to worldY] = shoreAt(worldX, worldY, LEVEL)
    }

    for (level in intArrayOf(LEVEL - 3, LEVEL - 1, LEVEL + 2, LEVEL + 4)) {
      for ((point, expected) in reference) {
        val actual = shoreAt(point.first, point.second, level)
        assertEquals(
          expected,
          actual,
          "shore at ${point.first}, ${point.second} is $expected at L$LEVEL but $actual at L$level"
        )
      }
    }

    assertTrue(
      reference.values.any { it > 0.0 } && reference.values.any { it < 0.0 },
      "the sampled run never crossed the shore, so it proved nothing: ${reference.values.take(4)}"
    )
  }

  /** [TerrainRaster.shore] at one world point, read through a viewport of the given scale. */
  private fun shoreAt(worldX: Double, worldY: Double, level: Int): Double {
    val view = viewAt(worldX, worldY, level, 1, 1)
    val raster = TerrainRaster.sample(view, inputs, AtlasPalette.PARCHMENT)
    return raster.shore[raster.index(0, 0)]
  }

  private fun assertContentEqualsAt(expected: IntArray, actual: IntArray, what: String) {
    assertEquals(expected.size, actual.size, "$what has a different size")
    for (i in expected.indices) {
      assertEquals(expected[i], actual[i], "$what differs at pixel $i")
    }
  }

  private fun pixelsOf(image: BufferedImage): IntArray =
    (image.raster.dataBuffer as DataBufferInt).data.copyOf()

  private fun hex(rgb: Int) = "#%06x".format(rgb and 0xFFFFFF)

  private fun viewAt(x: Double, y: Double, level: Int, width: Int, height: Int) = Viewport(
    centerX = x,
    centerY = y,
    metresPerPixel = Math.pow(2.0, level.toDouble()),
    widthPx = width,
    heightPx = height
  )

  private companion object {

    /** Small enough to generate quickly, large enough to contain a coast, a wood and some relief. */
    val CONFIG = WorldConfig(seed = StandardWorld.DEFAULT_SEED, widthCells = 128, heightCells = 128)

    /**
     * Built once for the class. Generating a world is a second or two, and every test here wants the same
     * one - the point is to compare renders of it, not to compare worlds.
     */
    val inputs: TileInputs by lazy { TileInputs.of(StandardWorld.build(CONFIG)) }

    val style = AtlasStyle()

    /** A coast: land to the west, sea to the east. Found with `mapInspect -Pcoast`. */
    const val CENTRE_X = 51450.0
    const val CENTRE_Y = 63500.0

    /** 64 m per pixel, so a 96-pixel view spans 6 km and holds shore, relief and cover at once. */
    const val LEVEL = 6

    const val TILE = 64

    /** Points along the walk across the coast. Enough to cross it and to cost a fraction of a second. */
    const val CROSS_ZOOM_SAMPLES = 100
  }
}
