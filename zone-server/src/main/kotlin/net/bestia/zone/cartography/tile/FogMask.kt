package net.bestia.zone.cartography.tile

import net.bestia.worldgen.fields.DistanceTransform
import net.bestia.worldgen.vector.Aabb
import net.bestia.zone.cartography.coverage.Coverage
import net.bestia.zone.cartography.coverage.SurveyGrid
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * How much of one tile a chart set reveals, as a per-pixel alpha.
 *
 * ### The mask is applied here, not on the client
 *
 * A client could be modified, so it must never hold imagery for ground its player has not charted. That rules
 * out the arrangement most slippy maps use - immutable tiles plus a separate mask, composited locally - because
 * at the coarsest level a single tile *is* the whole world. So the alpha is baked into the tile before it is
 * served, and a wholly uncharted tile is never rendered at all.
 *
 * ### Transparency in a PNG is not concealment
 *
 * PNG stores the colour channels of a fully transparent pixel like any other. A mask that only wrote alpha would
 * ship every uncharted pixel's true colour to anyone willing to read the file rather than display it - the exact
 * threat the baking is for. [applyTo] therefore **zeroes the colour wherever the alpha rounds to zero**, and
 * that pairing is the invariant to preserve if this is ever rewritten: alpha zero implies colour zero.
 *
 * ### The falloff ramps inward
 *
 * A soft edge means some pixels are partly disclosed, and the direction the softness runs decides *which*. The
 * distance field is measured from the nearest **uncharted** cell, so a cell on the boundary is dimmed and
 * revealing never spills past what the chart holds. Ramping outward would have been the same code and would have
 * leaked a strip of unknown ground all the way round every chart.
 *
 * ### Grid resolution, and why the mask has a halo
 *
 * The mask is evaluated on a lattice of [SurveyGrid.CELL_METRES], or of one pixel where a pixel is coarser than
 * a cell - so at most 256 rows for any tile at any level, and exactly the survey grid at every level up to L6.
 * Above that a lattice cell holds several survey cells and takes the coverage of the one at its centre, which is
 * a point sample: at 512 m per pixel the error is half a pixel.
 *
 * That lattice is anchored to the world, never to the tile, and is extended by a halo wide enough to reach past
 * the falloff. Both are the same requirement: the distance field inside a tile has to be the distance field the
 * neighbouring tile computes for the same ground, or the ramp would restart at every tile edge and the fog would
 * draw the tile grid. Anchoring is what makes the lattices coincide - a tile boundary is always a multiple of
 * the lattice step, in both regimes - and the halo is what makes the values on it agree.
 */
class FogMask private constructor(
  private val tile: TileId,
  private val reveal: FloatArray,
  private val lattice: Int,
  private val halo: Int,
  private val step: Double,

  /** No pixel is revealed. The tile must not be rendered or served. */
  val isFullyHidden: Boolean,

  /** Every pixel is fully revealed, so the unmasked tile is the right answer. */
  val isFullyClear: Boolean
) {

  /**
   * Copies an opaque tile into an ARGB one with the mask applied.
   *
   * The alpha is quantised to [ALPHA_LEVELS], for the reason `TileRenderer.posterize` quantises colour: the
   * falloff is a smooth gradient over up to 256 pixels and a full-depth one gives DEFLATE a different value
   * almost every pixel. Sixteen steps across a fringe this soft is not visible.
   */
  fun applyTo(image: BufferedImage): BufferedImage {
    val masked = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
    val source = rgbOf(image)
    val target = (masked.raster.dataBuffer as DataBufferInt).data

    val metresPerPixel = tile.metresPerPixel
    val bounds = tile.bounds

    for (py in 0 until image.height) {
      // Rows run south down the image, world y runs north.
      val worldY = bounds.maxY - (py + 0.5) * metresPerPixel
      val row = py * image.width

      for (px in 0 until image.width) {
        val worldX = bounds.minX + (px + 0.5) * metresPerPixel
        val alpha = quantise(revealAt(worldX, worldY, bounds))

        // Alpha zero must mean colour zero: see the class note on PNG transparency.
        target[row + px] = if (alpha == 0) 0 else (alpha shl 24) or (source[row + px] and 0xFFFFFF)
      }
    }

    return masked
  }

  /**
   * The tile's colours as packed ints.
   *
   * A tile straight off the renderer is `TYPE_INT_RGB` and lends its own array; one decoded from the tile
   * store is whatever `ImageIO` chose for it - `TYPE_3BYTE_BGR`, normally - and is converted. The branch lives
   * here rather than at the call site because the alternative is every caller knowing which of the two it
   * holds, and being wrong about it is a `ClassCastException` at request time.
   */
  private fun rgbOf(image: BufferedImage): IntArray =
    if (image.type == BufferedImage.TYPE_INT_RGB || image.type == BufferedImage.TYPE_INT_ARGB) {
      (image.raster.dataBuffer as DataBufferInt).data
    } else {
      image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    }

  /** Bilinear, so the fringe is smooth at close zoom where one lattice cell spans many pixels. */
  private fun revealAt(worldX: Double, worldY: Double, bounds: Aabb): Float {
    val gx = (worldX - bounds.minX) / step + halo - 0.5
    val gy = (worldY - bounds.minY) / step + halo - 0.5

    val x0 = floor(gx).toInt().coerceIn(0, lattice - 1)
    val y0 = floor(gy).toInt().coerceIn(0, lattice - 1)
    val x1 = (x0 + 1).coerceAtMost(lattice - 1)
    val y1 = (y0 + 1).coerceAtMost(lattice - 1)

    val fx = (gx - x0).coerceIn(0.0, 1.0).toFloat()
    val fy = (gy - y0).coerceIn(0.0, 1.0).toFloat()

    val south = reveal[y0 * lattice + x0] * (1f - fx) + reveal[y0 * lattice + x1] * fx
    val north = reveal[y1 * lattice + x0] * (1f - fx) + reveal[y1 * lattice + x1] * fx

    return south * (1f - fy) + north * fy
  }

  companion object {

    /**
     * How far the fringe reaches inward from the edge of a chart, in metres.
     *
     * Two and a half cells, which is the softest edge the lattice can express without eating a noticeable part
     * of the smallest survey - a kilometre-radius chart loses a sixth of its radius to the fringe at this
     * setting. Lower it for a crisper boundary; the look is the only thing that depends on it.
     */
    const val FALLOFF_METRES = 2.5 * SurveyGrid.CELL_METRES

    /** Distinct alpha values a masked tile uses. */
    const val ALPHA_LEVELS = 16

    /**
     * The area whose complete coverage means a tile needs no mask.
     *
     * Wider than the tile by the falloff, and that asymmetry is the point. Coverage that stops exactly on a tile
     * edge still puts a fringe *inside* that tile, so a tile whose own bounds are fully charted is not
     * necessarily fully clear - serving it unmasked on that test would show a hard edge where its neighbour
     * shows a soft one, which is a seam. Asking about the wider area is the condition that actually holds.
     *
     * The opposite test needs no widening: a tile with no coverage of its own is uniformly unrevealed whatever
     * its neighbours hold, because the distance from uncharted ground is zero in every one of its cells.
     */
    fun clearingArea(tile: TileId): Aabb = tile.bounds.expanded(FALLOFF_METRES)

    /**
     * Every scrap of ground the mask for a tile is built from: the tile, plus the halo the lattice extends by.
     *
     * This is the area a masked tile may be **keyed** on, and the reason it is not [clearingArea] is that
     * [clearingArea] is up to one lattice step narrower. A digest taken over anything the mask reads outside
     * of it can be equal for two chart sets whose fringes differ - a cache handing one player another
     * player's fog, which is the failure this rules out by construction rather than by argument.
     *
     * Emphatically not the area to test for "needs no mask": that is [clearingArea], which is the *narrowest*
     * area whose completeness makes the fringe vanish, and widening it there would serve a masked tile where
     * a shared one was correct.
     */
    fun readArea(tile: TileId): Aabb = stepFor(tile).let { tile.bounds.expanded(haloFor(it) * it) }

    /** Lattice pitch: the survey cell, or one pixel where a pixel is the coarser of the two. */
    private fun stepFor(tile: TileId): Double = maxOf(SurveyGrid.CELL_METRES, tile.metresPerPixel)

    /** Cells of margin, wide enough that the falloff is computed from the same ground the neighbour uses. */
    private fun haloFor(step: Double): Int = ceil(FALLOFF_METRES / step).toInt() + 1

    fun forTile(tile: TileId, coverage: Coverage): FogMask {
      val step = stepFor(tile)
      val across = (tile.span / step).roundToInt()
      val halo = haloFor(step)
      val lattice = across + 2 * halo

      val charted = BooleanArray(lattice * lattice)
      for (gy in 0 until lattice) {
        val worldY = tile.bounds.minY + (gy - halo + 0.5) * step
        for (gx in 0 until lattice) {
          val worldX = tile.bounds.minX + (gx - halo + 0.5) * step
          charted[gy * lattice + gx] = coverage.contains(worldX, worldY)
        }
      }

      val distance = DistanceTransform.euclidean(lattice, lattice) { x, y -> !charted[y * lattice + x] }

      val reveal = FloatArray(lattice * lattice)
      for (i in reveal.indices) {
        val metres = distance.data[i]
        // An empty mask - every lattice cell charted - reports MAX_VALUE rather than a distance.
        reveal[i] = if (metres == Double.MAX_VALUE) 1f
        else (metres * step / FALLOFF_METRES).coerceAtMost(1.0).toFloat()
      }

      // One cell wider than the tile in each direction, because a border pixel's bilinear sample reaches into
      // the first halo cell. Scanning only the interior would call a tile fully clear while `applyTo` dimmed its
      // edge, and the tile service would then serve the opaque base tile where its neighbour showed a fringe.
      var hidden = true
      var clear = true
      for (gy in halo - 1..halo + across) {
        for (gx in halo - 1..halo + across) {
          val value = reveal[gy * lattice + gx]
          if (value > 0f) hidden = false
          if (value < 1f) clear = false
        }
      }

      return FogMask(tile, reveal, lattice, halo, step, hidden, clear)
    }

    private fun quantise(reveal: Float): Int {
      val steps = ALPHA_LEVELS - 1
      return (reveal * steps).roundToInt().coerceIn(0, steps) * 255 / steps
    }
  }
}
