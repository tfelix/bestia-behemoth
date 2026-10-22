package net.bestia.zone.cartography.tile

import net.bestia.worldgen.fields.DistanceTransform
import net.bestia.worldgen.vector.Aabb
import net.bestia.zone.cartography.coverage.Coverage
import net.bestia.zone.cartography.coverage.SurveyGrid
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
 * fade is measured from the edge of the chart and runs inward, so revealing never spills past what the chart
 * holds. Ramping outward would have been the same code and would have leaked a strip of unknown ground all the
 * way round every chart.
 *
 * ### Two regimes, and why a survey cell must not be what you see
 *
 * A chart is bits on a lattice of [SurveyGrid.CELL_METRES], so its edge is a staircase of 64 m steps. Where a
 * pixel is finer than a cell - L5 and closer, which is where anybody looks at the edge - that staircase is
 * 16 pixels a step at 4 m per pixel, and any fade that is merely interpolated between lattice values shows it:
 * the contour follows the cells, and the fade is banded into steps of its own. So up to and including L6, where a
 * pixel is a cell, the reveal is not read off the lattice but **computed from it at pixel resolution**: the
 * charted cells are smoothed with a Gaussian of [SIGMA_METRES], integrated exactly over each cell, and the result
 * is turned into an alpha by [ramp]. The smoothed edge runs through the middle of the staircase, which is as
 * close to the disc a survey drew as the bits allow, and it is round at every zoom because it is a function of
 * world position and not of the tile.
 *
 * That alone would reveal a sliver of uncharted ground wherever the smoothed contour bulges past a step, so the
 * ramp starts inside the contour ([RAMP_FROM]) and a pixel whose own cell is uncharted is **forced to zero**.
 * Both are what keeps the fringe inside the chart, which the earlier interpolated fade did not: it disclosed up
 * to about half a cell beyond a charted cell.
 *
 * Coarser than that - L7 and up, where a pixel is wider than a cell - the lattice is one cell per pixel, the
 * staircase is below the resolution of the picture, and the mask is the distance field it always was: the
 * distance from the nearest uncharted cell, bilinear, a point sample at the centre of the cell.
 *
 * ### Grid resolution, and why the mask has a halo
 *
 * The lattice is [SurveyGrid.CELL_METRES], or one pixel where a pixel is coarser than a cell - so at most 256
 * rows for any tile at any level, and exactly the survey grid at every level up to L6.
 *
 * That lattice is anchored to the world, never to the tile, and is extended by a halo wide enough to reach past
 * [REACH_METRES]. Both are the same requirement: the reveal inside a tile has to be the reveal the neighbouring
 * tile computes for the same ground, or the fade would restart at every tile edge and the fog would draw the
 * tile grid. Anchoring is what makes the lattices coincide - a tile boundary is always a multiple of the lattice
 * step, in both regimes - and the halo is what makes the values on it agree.
 */
class FogMask private constructor(
  /** Per pixel, row-major with the north edge first, already quantised to what is written. */
  private val alpha: IntArray,

  /** No pixel is revealed. The tile must not be rendered or served. */
  val isFullyHidden: Boolean,

  /** Every pixel is fully revealed, so the unmasked tile is the right answer. */
  val isFullyClear: Boolean
) {

  /**
   * Copies an opaque tile into an ARGB one with the mask applied.
   *
   * The alpha is quantised, for the reason `TileRenderer.posterize` quantises colour: a fade is a smooth gradient
   * over up to 256 pixels and a full-depth one gives DEFLATE a different value almost every pixel. How coarsely
   * is [ALPHA_LEVELS] at the levels where the fade is a pixel or two wide, and [SMOOTH_ALPHA_LEVELS] where it
   * spans enough pixels for steps to show.
   */
  fun applyTo(image: BufferedImage): BufferedImage {
    require(image.width == TileId.TILE_PIXELS && image.height == TileId.TILE_PIXELS) {
      "A mask is for a ${TileId.TILE_PIXELS} pixel tile, not ${image.width}x${image.height}"
    }

    val masked = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
    val source = rgbOf(image)
    val target = (masked.raster.dataBuffer as DataBufferInt).data

    for (i in target.indices) {
      val a = alpha[i]

      // Alpha zero must mean colour zero: see the class note on PNG transparency.
      target[i] = if (a == 0) 0 else (a shl 24) or (source[i] and 0xFFFFFF)
    }

    return masked
  }

  /** Like [applyTo], for an overlay with an alpha of its own, such as a habitat layer: the two alphas multiply. */
  fun applyToOverlay(overlay: BufferedImage): BufferedImage {
    require(overlay.type == BufferedImage.TYPE_INT_ARGB) { "An overlay is ARGB, not image type ${overlay.type}" }

    val masked = BufferedImage(overlay.width, overlay.height, BufferedImage.TYPE_INT_ARGB)
    val source = (overlay.raster.dataBuffer as DataBufferInt).data
    val target = (masked.raster.dataBuffer as DataBufferInt).data

    for (i in target.indices) {
      val a = (source[i] ushr 24) * alpha[i] / 255
      target[i] = if (a == 0) 0 else (a shl 24) or (source[i] and 0xFFFFFF)
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

  companion object {

    /**
     * How far the fade reaches inward from the edge of a chart at the coarse levels, in metres.
     *
     * Two and a half cells, which is the softest edge the lattice can express without eating a noticeable part
     * of the smallest survey - a kilometre-radius chart loses a sixth of its radius to the fringe at this
     * setting. Lower it for a crisper boundary; the look is the only thing that depends on it. The fine levels
     * have their own width, [SIGMA_METRES].
     */
    const val FALLOFF_METRES = 2.5 * SurveyGrid.CELL_METRES

    /**
     * Standard deviation of the Gaussian the charted cells are smoothed with at the fine levels, in metres.
     *
     * Two and a half cells again, and not by accident: the fade from the contour to fully revealed is then about
     * the width of [FALLOFF_METRES], so the edge keeps its softness across the change of regime. Narrower and the
     * edge keeps more of the survey's terraces - a disc drawn on a grid has runs of cells with a one cell step
     * between them, which a short filter leaves as a ripple; wider and the fade eats into small charts.
     */
    const val SIGMA_METRES = 2.5 * SurveyGrid.CELL_METRES

    /**
     * How far from a pixel a charted cell can still change its alpha, in metres: three standard deviations, the
     * Gaussian's tail beyond which is under a quarter of a percent and is cut and renormalised.
     *
     * This, and not [FALLOFF_METRES], is what the areas below widen a tile by, because it is the larger of the two.
     */
    const val REACH_METRES = 3 * SIGMA_METRES

    /** Distinct alpha values a masked tile uses at the coarse levels, where the fade is a pixel or two wide. */
    const val ALPHA_LEVELS = 16

    /**
     * Distinct alpha values at the fine levels. A fade of 160 m is 160 pixels at 1 m per pixel, and sixteen steps
     * over that are visible as bands; sixty-four are not, and only the tiles an edge crosses pay for them.
     */
    const val SMOOTH_ALPHA_LEVELS = 64

    /**
     * Bumped whenever the pixels a given chart set produces change. It is in the ETag of every masked tile, which
     * is otherwise made of the chart digest alone: a client holding an old render would be told, correctly by that
     * key, that nothing changed, and would keep the old edge for as long as its charts did.
     */
    const val REVISION = 2

    /**
     * Where, in smoothed coverage, the alpha starts rising. Half is the middle of the staircase; a little above is
     * inside it, so the sliver of ground the smoothed contour bulges over is not revealed. Measured on a disc: at
     * 0.5 it reveals up to 19 m beyond a charted cell, at 0.6 none.
     */
    private const val RAMP_FROM = 0.6

    /** Where the alpha reaches one: with [RAMP_FROM], a fade about as wide as [FALLOFF_METRES]. */
    private const val RAMP_TO = 0.91

    /**
     * The area whose complete coverage means a tile needs no mask.
     *
     * Wider than the tile by [REACH_METRES], and that asymmetry is the point. Coverage that stops exactly on a
     * tile edge still puts a fringe *inside* that tile, so a tile whose own bounds are fully charted is not
     * necessarily fully clear - serving it unmasked on that test would show a hard edge where its neighbour
     * shows a soft one, which is a seam. Asking about the wider area is the condition that actually holds.
     *
     * The opposite test needs no widening: a tile with no coverage of its own is uniformly unrevealed whatever
     * its neighbours hold, because the distance from uncharted ground is zero in every one of its cells.
     */
    fun clearingArea(tile: TileId): Aabb = tile.bounds.expanded(REACH_METRES)

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

    /** Cells of margin, wide enough that the fade is computed from the same ground the neighbour uses. */
    private fun haloFor(step: Double): Int = ceil(REACH_METRES / step).toInt() + 1

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

      val alpha = if (tile.metresPerPixel <= SurveyGrid.CELL_METRES) {
        smoothAlpha(tile, charted, lattice, halo)
      } else {
        latticeAlpha(tile, charted, lattice, halo, step)
      }

      return FogMask(alpha, alpha.all { it == 0 }, alpha.all { it == 255 })
    }

    /**
     * The fine levels: the charted cells smoothed at pixel resolution. See the class note for why.
     *
     * Separable, so a pixel costs two short sums rather than a square one. The Gaussian is integrated over each
     * cell exactly instead of sampled at its centre, which is what makes the result the smoothing of the *cells*
     * - squares with edges - and not of points; at 64 m per pixel the two differ, and the tile test that the field
     * is one function of world position at every level depends on it.
     */
    private fun smoothAlpha(tile: TileId, charted: BooleanArray, lattice: Int, halo: Int): IntArray {
      val size = TileId.TILE_PIXELS
      val cell = SurveyGrid.CELL_METRES
      val sigma = SIGMA_METRES / cell
      val support = REACH_METRES / cell
      val taps = ceil(2 * support).toInt() + 2
      val pixelCells = tile.metresPerPixel / cell

      val columns = Taps(size, taps)
      val rows = Taps(size, taps)
      for (p in 0 until size) {
        // Lattice coordinates, in cells, with the tile's west and north edges at `halo` and rows counted north up.
        columns.fill(p, (p + 0.5) * pixelCells + halo, sigma, support)
        rows.fill(p, tile.span / cell - (p + 0.5) * pixelCells + halo, sigma, support)
      }

      // Each lattice row, smoothed along x at every pixel column.
      val along = FloatArray(lattice * size)
      for (gy in 0 until lattice) {
        val base = gy * lattice
        for (px in 0 until size) {
          var sum = 0f
          val from = columns.from[px]
          for (k in 0 until taps) {
            if (charted[base + from + k]) sum += columns.weight[px * taps + k]
          }
          along[gy * size + px] = sum
        }
      }

      val alpha = IntArray(size * size)
      for (py in 0 until size) {
        val cellY = rows.cell[py]
        val from = rows.from[py]

        for (px in 0 until size) {
          // A pixel in an uncharted cell shows nothing, whatever the smoothing says: the fringe stays inside.
          if (!charted[cellY * lattice + columns.cell[px]]) continue

          var smoothed = 0f
          for (k in 0 until taps) smoothed += rows.weight[py * taps + k] * along[(from + k) * size + px]

          alpha[py * size + px] = quantise(ramp(smoothed), SMOOTH_ALPHA_LEVELS)
        }
      }

      return alpha
    }

    /**
     * The coarse levels: the lattice is one cell per pixel, so the alpha is the lattice's own reveal, bilinear.
     */
    private fun latticeAlpha(tile: TileId, charted: BooleanArray, lattice: Int, halo: Int, step: Double): IntArray {
      val distance = DistanceTransform.euclidean(lattice, lattice) { x, y -> !charted[y * lattice + x] }

      val reveal = FloatArray(lattice * lattice)
      for (i in reveal.indices) {
        val metres = distance.data[i]
        // An empty mask - every lattice cell charted - reports MAX_VALUE rather than a distance.
        reveal[i] = if (metres == Double.MAX_VALUE) 1f
        else (metres * step / FALLOFF_METRES).coerceAtMost(1.0).toFloat()
      }

      val size = TileId.TILE_PIXELS
      val alpha = IntArray(size * size)
      val metresPerPixel = tile.metresPerPixel
      val bounds = tile.bounds

      for (py in 0 until size) {
        // Rows run south down the image, world y runs north.
        val worldY = bounds.maxY - (py + 0.5) * metresPerPixel

        for (px in 0 until size) {
          val worldX = bounds.minX + (px + 0.5) * metresPerPixel
          alpha[py * size + px] = quantise(revealAt(reveal, lattice, halo, step, worldX, worldY, bounds), ALPHA_LEVELS)
        }
      }

      return alpha
    }

    /** Bilinear between lattice values. */
    private fun revealAt(
      reveal: FloatArray,
      lattice: Int,
      halo: Int,
      step: Double,
      worldX: Double,
      worldY: Double,
      bounds: Aabb
    ): Float {
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

    /** Smoothed coverage to a reveal: nothing up to [RAMP_FROM], everything from [RAMP_TO], smooth between. */
    private fun ramp(smoothed: Float): Float {
      val t = ((smoothed - RAMP_FROM) / (RAMP_TO - RAMP_FROM)).toFloat().coerceIn(0f, 1f)
      return t * t * (3f - 2f * t)
    }

    private fun quantise(reveal: Float, levels: Int): Int {
      val steps = levels - 1
      return (reveal * steps).roundToInt().coerceIn(0, steps) * 255 / steps
    }

    /**
     * For each pixel along one axis, which lattice cells its smoothing reads and with what weights.
     *
     * The weights are the Gaussian's mass over each cell, cut at [fill]'s `support` and renormalised so they sum
     * to one. That is what makes a fully charted neighbourhood exactly one and an empty one exactly zero - the two
     * cases the tile service decides on - rather than 0.9973 and 0.0027.
     */
    private class Taps(pixels: Int, private val taps: Int) {
      /** First lattice cell read, per pixel. */
      val from = IntArray(pixels)

      /** The cell the pixel's centre lies in, per pixel. */
      val cell = IntArray(pixels)

      /** [taps] weights per pixel, zero past the last cell it reads. */
      val weight = FloatArray(pixels * taps)

      fun fill(pixel: Int, at: Double, sigma: Double, support: Double) {
        val first = floor(at - support).toInt()
        val last = floor(at + support).toInt()
        from[pixel] = first
        cell[pixel] = floor(at).toInt()

        // A cell's weight is the Gaussian's mass over the part of it inside the window.
        var total = 0.0
        for (i in first..last) {
          val low = maxOf(i.toDouble(), at - support)
          val high = minOf(i + 1.0, at + support)
          val mass = cumulative((high - at) / sigma) - cumulative((low - at) / sigma)
          weight[pixel * taps + (i - first)] = mass.toFloat()
          total += mass
        }

        for (k in 0..last - first) weight[pixel * taps + k] = (weight[pixel * taps + k] / total).toFloat()
      }

      /** The standard normal's cumulative distribution, by Abramowitz and Stegun 7.1.26: good to 1.5e-7. */
      private fun cumulative(z: Double): Double {
        val x = abs(z) / sqrt(2.0)
        val t = 1.0 / (1.0 + 0.3275911 * x)
        val poly = ((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592
        val erf = 1.0 - poly * t * exp(-x * x)

        return if (z >= 0) 0.5 * (1.0 + erf) else 0.5 * (1.0 - erf)
      }
    }
  }
}
