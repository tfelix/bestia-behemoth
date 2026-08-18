package net.bestia.zone.cartography.render

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.render.Colors
import net.bestia.worldgen.render.Hillshade
import net.bestia.worldgen.render.Viewport
import kotlin.math.roundToLong

/**
 * Relief drawn the way a pen draws it: banded, then hatched, never smoothly shaded.
 *
 * Smooth hillshading is what `viewer/MapRenderer` does and it is the right answer there, because a
 * continuous ramp is how you read a *value* off a picture. It is the wrong answer here. A gradient is the
 * one mark a pen cannot make, so a smoothly shaded map reads as a rendering of terrain no matter what else
 * is drawn on it, and the glyphs sitting on top of it read as stickers.
 *
 * So the shade is quantised to [BANDS] steps and the dark steps are filled with 45-degree hatching whose
 * spacing tightens as the slope turns away from the light. Two marks, both of them things a nib does: a flat
 * tone and a line.
 *
 * ### Two spaces, deliberately
 *
 * The *band* is a function of world relief, so it belongs to the ground and is identical wherever the same
 * hillside is drawn. The *hatch lines* are a function of paper position, like [Parchment], so they run
 * unbroken across a tile boundary and do not magnify when you zoom. Mixing the two up is what produces
 * either hatching that swims over the ground as you pan, or a seam at every tile edge.
 */
object InkRelief {

  /**
   * Bands the shade over [TerrainRaster.ground] and lays it into [into], which is viewport-sized.
   *
   * @param detailHeight extra high-frequency relief per pixel, or null at zooms where the kilometre raster
   *   is already the finest honest answer. See [DetailRelief.MAX_METRES_PER_PIXEL].
   */
  fun apply(
    into: IntArray,
    view: Viewport,
    terrain: TerrainRaster,
    palette: AtlasPalette,
    seed: Long,
    detailHeight: DoubleArray? = null,
    /**
     * Whether the dark bands are filled with hatching as well as toned.
     *
     * Off for [PlanStyle]. Hatching is a convention for relief on a *drawn* map, and a plan is not one - it is
     * a survey, so a hillside there wants a flat tone and nothing else. Left on, the strokes read as texture on
     * the ground the buildings stand on.
     */
    hatch: Boolean = true
  ) {
    val heights = if (detailHeight == null) {
      terrain.ground
    } else {
      DoubleArray(terrain.ground.size) { terrain.ground[it] + detailHeight[it] }
    }

    val shade = Hillshade.shade(
      heights, terrain.width, terrain.height, view.metresPerPixel, EXAGGERATION
    )

    val originX = (view.minX / view.metresPerPixel).roundToLong()
    val originY = (view.minY / view.metresPerPixel).roundToLong()
    val hatchSeed = GenRng.mix64(seed xor HATCH_SALT)

    for (py in 0 until view.heightPx) {
      val paperY = originY + (view.heightPx - py - 1)

      for (px in 0 until view.widthPx) {
        val i = py * view.widthPx + px
        val t = terrain.index(px, py)

        // Water takes no relief. The sea is flat and a lake surface is flat, and shading the bed through
        // the water is the artefact `viewer/WorldMapField` calls out by name.
        if (terrain.shore[t] > 0.0) continue
        if (terrain.ground[t].isNaN()) continue

        val paperX = originX + px
        val band = bandOf(shade[t])

        into[i] = Colors.scale(into[i], BAND_TONE[band])

        val spacing = HATCH_SPACING[band]
        if (hatch && spacing > 0 && onHatchLine(paperX, paperY, spacing, hatchSeed)) {
          into[i] = Colors.mix(into[i], palette.ink, HATCH_STRENGTH[band])
        }
      }
    }
  }

  /**
   * Which of [BANDS] steps a brightness multiplier falls into.
   *
   * [Hillshade] returns values in `[ambient, 1.6]` normalised so that flat ground is exactly 1.0. The band
   * edges are placed around that 1.0 rather than spread evenly over the range, so a flat plain lands
   * squarely in the middle band and takes neither tone nor hatching - the reason a map of the Reach comes
   * out as bare paper instead of uniformly grey.
   */
  private fun bandOf(brightness: Double): Int {
    for (b in BAND_EDGES.indices) {
      if (brightness < BAND_EDGES[b]) return b
    }
    return BANDS - 1
  }

  /**
   * Whether a paper pixel sits on a 45-degree hatch line of the given spacing.
   *
   * The line family is `x + y = k * spacing`, which is exact in integers and therefore seamless: two
   * adjacent tiles compute the same `k` for the same paper pixel with no shared state. The noise term
   * wobbles the phase by up to a pixel so the lines are not mechanically parallel, which is most of what
   * separates hatching from a halftone screen.
   */
  private fun onHatchLine(paperX: Long, paperY: Long, spacing: Int, seed: Long): Boolean {
    val wobble = Noise.value2d(
      seed, paperX * WOBBLE_FREQUENCY, paperY * WOBBLE_FREQUENCY
    ) * WOBBLE_PIXELS

    val diagonal = paperX + paperY + wobble.roundToLong()
    return Math.floorMod(diagonal, spacing.toLong()) == 0L
  }

  /** Vertical multiplier. One is honest and unreadable at map scale; see [Hillshade.shade]. */
  private const val EXAGGERATION = 2.4

  private const val BANDS = 5

  /**
   * Upper brightness bound of each band except the last. Tight around 1.0 on purpose - see [bandOf].
   */
  private val BAND_EDGES = doubleArrayOf(0.72, 0.93, 1.04, 1.22)

  /** Tone multiplier per band. The middle band is exactly 1.0: flat ground is untouched paper. */
  private val BAND_TONE = doubleArrayOf(0.87, 0.94, 1.0, 1.035, 1.07)

  /**
   * Hatch line spacing in pixels per band; 0 means no hatching. Tighter is darker.
   *
   * Only the darkest band is hatched. Hatching the second one as well doubled the inked area, and since a
   * hillside spends most of its area part-lit, the result was a wash of diagonal streaks over most of the
   * land - which then competes with the glyphs that are supposed to carry the relief at this scale.
   */
  private val HATCH_SPACING = intArrayOf(4, 0, 0, 0, 0)

  /** How far a hatched pixel is pulled towards the ink. */
  private val HATCH_STRENGTH = doubleArrayOf(0.20, 0.0, 0.0, 0.0, 0.0)

  private const val WOBBLE_FREQUENCY = 1.0 / 26.0
  private const val WOBBLE_PIXELS = 1.6

  private const val HATCH_SALT = 0x3C6E_F372_FE94_F82BL
}
