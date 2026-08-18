package net.bestia.zone.cartography.render

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.render.Colors
import net.bestia.worldgen.render.Viewport
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * The paper the map is drawn on: mottling, fibre streaks and a faint stain.
 *
 * ### Why this is a function of pixels and not of the world
 *
 * Paper grain does not belong to the world. Tie it to world coordinates and it zooms with the map, so at
 * close range a fibre becomes a hundred-metre feature that looks like terrain, and the same patch of ground
 * carries a different grain at every zoom for no reason a reader can name.
 *
 * So the texture is a function of **paper position**: the pixel's index in the infinite sheet that this
 * zoom level is drawn on, `round(viewport.minX / metresPerPixel) + px`. That has the two properties a tiled
 * map needs and world-space would not give:
 *
 * - **continuous across tiles at one level**, because neighbouring tiles' paper indices are contiguous, so
 *   the grain runs straight through a tile boundary with no seam and no repeat;
 * - **independent between levels**, which is correct rather than a compromise - zooming in on real paper
 *   does not magnify its fibres, and a level's grain is at its own scale by construction.
 *
 * The rounding is exact in practice: every level's `metresPerPixel` is a power of two and every tile origin
 * is a whole number of tiles along, so `minX / metresPerPixel` is an integer before it is rounded. The
 * rounding is there so that an off-grid viewport - the render tool pointed at an arbitrary centre - still
 * lands on a stable lattice instead of shimmering as it pans.
 */
object Parchment {

  /**
   * Tints [into] with the paper texture in place.
   *
   * Applied to the already-coloured ground rather than drawn under it, because the mottling has to read on
   * land and on sea alike; laying paper down first and painting over it would leave the sea flat, and the
   * sea is where the texture does most of its work.
   */
  fun apply(into: IntArray, view: Viewport, seed: Long, strength: Double = 1.0) {
    if (strength <= 0.0) return

    val originX = (view.minX / view.metresPerPixel).roundToLong()
    val originY = (view.minY / view.metresPerPixel).roundToLong()
    val grainSeed = GenRng.mix64(seed xor GRAIN_SALT)
    val fibreSeed = GenRng.mix64(seed xor FIBRE_SALT)
    val stainSeed = GenRng.mix64(seed xor STAIN_SALT)

    for (py in 0 until view.heightPx) {
      // Paper y grows downwards with the pixel row; world y is irrelevant here by design.
      val paperY = (originY + (view.heightPx - py - 1)).toDouble()

      for (px in 0 until view.widthPx) {
        val paperX = (originX + px).toDouble()
        val i = py * view.widthPx + px

        val mottle = Noise.fbm(grainSeed, paperX * MOTTLE_FREQUENCY, paperY * MOTTLE_FREQUENCY, MOTTLE_OCTAVES)

        // Fibres are stretched forty to one along x, which is what makes them read as laid paper rather
        // than as more mottling at a finer scale.
        val fibre = Noise.value2d(fibreSeed, paperX * FIBRE_FREQUENCY_X, paperY * FIBRE_FREQUENCY_Y)

        // Ridged noise gives lobed blotches with hard edges, like a liquid stain, where fbm gives clouds.
        val stain = Noise.ridged(stainSeed, paperX * STAIN_FREQUENCY, paperY * STAIN_FREQUENCY, STAIN_OCTAVES)

        val lighten = (mottle * MOTTLE_AMOUNT + fibre * FIBRE_AMOUNT) * strength
        val darken = (softStain(stain) * STAIN_AMOUNT) * strength

        into[i] = Colors.scale(into[i], (1.0 + lighten - darken).coerceIn(0.5, 1.5))
      }
    }
  }

  /**
   * Keeps only the top of the ridged field, so stains are occasional blotches rather than a wash.
   *
   * Without the threshold the stain term is non-zero almost everywhere and simply darkens the whole sheet,
   * which the palette already decides.
   */
  private fun softStain(ridged: Double): Double {
    val above = abs(ridged) - STAIN_THRESHOLD
    return if (above <= 0.0) 0.0 else above / (1.0 - STAIN_THRESHOLD)
  }

  /** Deterministic per-pixel dither amplitude, for the grain that noise at these scales cannot give. */
  fun speck(seed: Long, paperX: Long, paperY: Long): Double =
    GenRng.hashUnit(seed, paperX, paperY) - 0.5

  /** Whole-pixel paper coordinate of a viewport pixel, for anything that needs the lattice directly. */
  fun paperOrigin(view: Viewport): Pair<Long, Long> =
    (view.minX / view.metresPerPixel).roundToLong() to (view.minY / view.metresPerPixel).roundToLong()

  /** The paper lattice a world span occupies, for callers reasoning in cells rather than pixels. */
  fun paperCell(value: Double, metresPerPixel: Double): Long = floor(value / metresPerPixel).toLong()

  private const val GRAIN_SALT = 0x1E37_79B9_7F4A_7C15L
  private const val FIBRE_SALT = 0x6A09_E667_F3BC_C909L
  private const val STAIN_SALT = 0x3B67_AE85_84CA_A73BL

  private const val MOTTLE_FREQUENCY = 1.0 / 48.0
  private const val MOTTLE_OCTAVES = 4
  private const val MOTTLE_AMOUNT = 0.030

  private const val FIBRE_FREQUENCY_X = 1.0 / 320.0
  private const val FIBRE_FREQUENCY_Y = 1.0 / 8.0
  private const val FIBRE_AMOUNT = 0.014

  private const val STAIN_FREQUENCY = 1.0 / 210.0
  private const val STAIN_OCTAVES = 2
  private const val STAIN_AMOUNT = 0.055
  private const val STAIN_THRESHOLD = 0.62
}
