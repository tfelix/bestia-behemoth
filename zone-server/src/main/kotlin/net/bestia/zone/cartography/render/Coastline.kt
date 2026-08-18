package net.bestia.zone.cartography.render

import net.bestia.worldgen.fields.DistanceTransform
import net.bestia.worldgen.render.Colors
import net.bestia.worldgen.render.Viewport
import kotlin.math.abs

/**
 * The shoreline, and the parallel lines a cartographer rules outside it.
 *
 * ### Why there is no coastline geometry here
 *
 * The obvious build is: march squares over the land mask, chain the segments into polylines, offset each
 * polyline seaward three times. That is also how you get a map with gaps in it. Offsetting a polyline
 * outwards fans it apart at every convex corner and folds it through itself at every concave one, and a
 * coastline is nothing but corners.
 *
 * This draws the same picture with no geometry at all. The shore is the set of pixels that sit across a sign
 * change in [TerrainRaster.shore] - a connected, closed, one-pixel line by construction - and the ruled lines
 * are the level sets of the *distance* to that set. One exact Euclidean distance transform per tile gives
 * every offset at once, exactly parallel, exactly gap-free, following every inlet.
 *
 * It is seamless for free, too. The shore field is a pure function of world position, so two tiles agree
 * about the seed set without sharing anything, and the halo is wide enough that they agree about the
 * distances as well - see [TerrainRaster.HALO].
 *
 * ### Why the distance is transformed rather than estimated
 *
 * The first version divided the field by its own gradient, which is the textbook first-order distance to a
 * zero crossing and is nearly free. It produced a coastline in *patches*. The reason is in the data: the
 * generator leaves a shelf two to four kilometres wide sitting within about twenty metres of sea level, so
 * along a coast the gradient of this field swings by two orders of magnitude. A threshold in pixels then
 * means a threshold of twenty metres of height in one place and twenty centimetres in the next, so the line
 * appears where the shelf happens to be steep and vanishes where it is flat. A distance transform has no
 * such sensitivity: it measures pixels, which is what the threshold is in.
 *
 * ### What the lines mean
 *
 * Nothing. They are a convention for "this edge is water", they read as depth without claiming a depth, and
 * they are the single strongest cue that a map was drawn rather than rendered. [FADE_PER_LINE] thins each one
 * after the first so the family reads as receding.
 */
object Coastline {

  /** How far out the ruled lines reach, in pixels. [TerrainRaster.HALO] must be at least this. */
  const val REACH_PIXELS = 10.5

  fun apply(into: IntArray, view: Viewport, terrain: TerrainRaster, palette: AtlasPalette) {
    val distance = DistanceTransform.euclidean(terrain.width, terrain.height) { x, y ->
      isShoreSeed(terrain, x, y)
    }

    for (py in 0 until view.heightPx) {
      for (px in 0 until view.widthPx) {
        val t = terrain.index(px, py)
        if (terrain.ground[t].isNaN()) continue

        val d = distance.data[t]
        if (d >= Double.MAX_VALUE) continue

        val i = py * view.widthPx + px

        // The stroke straddles the sign change, so it is inked from the land side as well as the water side.
        val onShore = 1.0 - d / SHORE_REACH_PIXELS
        if (onShore > 0.0) {
          into[i] = Colors.mix(into[i], palette.ink, onShore * SHORE_STRENGTH)
          continue
        }

        // Everything beyond the stroke is water only: a cartographer rules these in the sea, never inland.
        if (terrain.shore[t] <= 0.0) continue

        var strength = 0.0
        for (line in 1..LINES) {
          val nearness = 1.0 - abs(d - line * LINE_SPACING_PIXELS) / LINE_HALF_WIDTH_PIXELS
          if (nearness > 0.0) {
            strength = maxOf(strength, nearness * LINE_STRENGTH * FADE_PER_LINE[line - 1])
          }
        }

        if (strength > 0.0) {
          into[i] = Colors.mix(into[i], palette.waterInk, strength)
        }
      }
    }
  }

  /**
   * Whether a pixel sits across the land-water boundary.
   *
   * Compared against the four orthogonal neighbours only. Including the diagonals would seed both pixels of
   * every diagonal step, which thickens the line to two pixels wherever the coast runs at 45 degrees - and a
   * coast runs at 45 degrees most of the time.
   */
  private fun isShoreSeed(terrain: TerrainRaster, x: Int, y: Int): Boolean {
    val i = y * terrain.width + x
    if (terrain.ground[i].isNaN()) return false

    val wet = terrain.shore[i] > 0.0
    return differs(terrain, x - 1, y, wet) ||
        differs(terrain, x + 1, y, wet) ||
        differs(terrain, x, y - 1, wet) ||
        differs(terrain, x, y + 1, wet)
  }

  private fun differs(terrain: TerrainRaster, x: Int, y: Int, wet: Boolean): Boolean {
    if (x < 0 || y < 0 || x >= terrain.width || y >= terrain.height) return false

    val i = y * terrain.width + x
    if (terrain.ground[i].isNaN()) return false

    return (terrain.shore[i] > 0.0) != wet
  }

  /** Distance at which the shore stroke has faded out, giving a line a little over one pixel wide. */
  private const val SHORE_REACH_PIXELS = 1.35
  private const val SHORE_STRENGTH = 0.95

  private const val LINES = 3
  private const val LINE_SPACING_PIXELS = 3.1
  private const val LINE_HALF_WIDTH_PIXELS = 0.85
  private const val LINE_STRENGTH = 0.5

  /** Each successive line is fainter, which is what makes the family read as receding from the shore. */
  private val FADE_PER_LINE = doubleArrayOf(1.0, 0.62, 0.36)
}
