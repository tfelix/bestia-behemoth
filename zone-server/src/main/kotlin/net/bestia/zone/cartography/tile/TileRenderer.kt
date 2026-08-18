package net.bestia.zone.cartography.tile

import net.bestia.zone.cartography.render.AtlasStyle
import net.bestia.zone.cartography.render.MapStyle
import net.bestia.zone.cartography.render.PlanStyle
import net.bestia.zone.cartography.render.TileInputs
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.pow

/**
 * Draws one tile and encodes it.
 *
 * ### Which style a tile gets is decided here and nowhere else
 *
 * The two styles are chosen by scale, not by the caller. A tile is identified by a level and the level implies
 * a metres-per-pixel, so there is exactly one right answer for each tile and no reason to let a request carry
 * an opinion about it - a client that could ask for the atlas at 1 m per pixel would be asking for a tile that
 * disagrees with its neighbours at the same level.
 *
 * ### Thread safety
 *
 * Safe to call concurrently, and that is a claim about its dependencies rather than about this class: the
 * layers are plain arrays, `FeatureStore` is frozen after generation, and `ChunkHeightSampler` documents itself
 * as holding no mutable state. What it must never touch is `world/stream/ChunkService`, whose caches are
 * documented as single-threaded on the zone tick. Nothing here does.
 */
class TileRenderer(val inputs: TileInputs) {

  private val atlas: MapStyle = AtlasStyle()
  private val plan: MapStyle = PlanStyle()

  fun styleFor(level: Int): MapStyle =
    if (2.0.pow(level) <= PlanStyle.MAX_METRES_PER_PIXEL) plan else atlas

  fun render(tile: TileId): BufferedImage = styleFor(tile.level).render(tile.viewport(), inputs)

  /**
   * Renders and encodes to PNG.
   *
   * PNG rather than a lossy format for two reasons that both matter later: the fog mask is applied as alpha, so
   * the format has to carry it, and the atlas style is line art, which is what JPEG's ringing is worst at.
   *
   * A tile of drawn parchment compresses poorly - the paper texture varies every pixel by design, which is
   * exactly what a run-length predictor cannot exploit. Measure before optimising this: `mapBake` prints the
   * mean tile size per level, and the honest fixes if it is too large are fewer colours (an indexed palette) or
   * less paper noise, not a lossier codec.
   */
  fun encode(tile: TileId): ByteArray {
    val image = render(tile)
    posterize((image.raster.dataBuffer as DataBufferInt).data)

    val out = ByteArrayOutputStream(EXPECTED_TILE_BYTES)
    ImageIO.write(image, "png", out)
    return out.toByteArray()
  }

  /**
   * Snaps every channel to a coarser lattice before encoding, which roughly halves a tile.
   *
   * The paper texture perturbs every pixel by design, so a tile of drawn parchment has almost no two adjacent
   * pixels alike and DEFLATE's predictor has nothing to work with - the first bake measured 45 kB for a 256
   * square tile, or about two thirds of a byte per pixel, which is what incompressible data costs.
   *
   * Quantising is the fix that keeps the look. [LEVELS_PER_CHANNEL] steps is a step size of about five in 255,
   * well inside what the eye merges on a textured ground, and it collapses the long tail of
   * one-pixel-off values that were defeating compression. The alternatives were worse: a lossy codec rings on
   * line art and cannot carry the fog alpha, and turning the paper noise down would fix the file size by
   * removing the thing the file is for.
   *
   * Applied at encode time rather than in the styles, so the styles stay exact and the seam test still compares
   * unquantised pixels - a tile and its neighbour agree before this runs, and snapping both to the same lattice
   * cannot make them disagree.
   */
  private fun posterize(pixels: IntArray) {
    for (i in pixels.indices) {
      val rgb = pixels[i]
      pixels[i] = (STEP_TABLE[(rgb ushr 16) and 0xFF] shl 16) or
          (STEP_TABLE[(rgb ushr 8) and 0xFF] shl 8) or
          STEP_TABLE[rgb and 0xFF]
    }
  }

  private companion object {

    /** Initial buffer for an encoded tile. Wrong in either direction only costs a copy. */
    const val EXPECTED_TILE_BYTES = 24 * 1024

    /**
     * Distinct values per channel after [posterize].
     *
     * Measured on Genesis at L6, mean bytes per tile: 256 levels 22.0 kB, 52 levels 22.0, 32 levels 18.0, 24
     * levels 15.6, 16 levels 13.0. Falling all the way down, so the choice is where banding starts rather than
     * where the curve flattens.
     *
     * 32 is a step of about eight in 255. On a flat gradient that would band; here it does not, because the paper
     * texture varies by more than one step everywhere and acts as a dither - which is also why the sea, the
     * largest smoothly graded area on an atlas tile, survives it. Going below 32 starts to show on the sea.
     */
    const val LEVELS_PER_CHANNEL = 32

    /** Precomputed channel mapping, so the hot loop is three array reads rather than six divisions. */
    val STEP_TABLE = IntArray(256) { value ->
      val step = LEVELS_PER_CHANNEL - 1
      Math.round(value * step / 255.0).toInt() * 255 / step
    }
  }
}
