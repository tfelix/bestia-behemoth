package net.bestia.zone.cartography.render

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.render.Viewport
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The world sampled once onto a pixel grid, for every pass to read instead of re-sampling.
 *
 * Three passes want the ground height (relief, coastline, land tone) and all three want to
 * know whether a pixel is under water. Sampling per pass would mean three bicubic evaluations per pixel
 * and, worse, four chances for them to disagree about where the shore is.
 *
 * ### The halo
 *
 * The grid is [HALO] pixels larger than the viewport on every side, and the width is set by the widest thing
 * that reads a neighbour rather than by the commonest. Relief and the biome blend want one pixel;
 * [Coastline] runs a distance transform and rules lines out to [Coastline.REACH_PIXELS], so it wants that
 * many. A pixel whose true distance to the shore is within reach has its nearest shore pixel within reach as
 * well, so a halo of that width makes the transform exact for every distance the drawing can use - and a
 * narrower one would quietly overestimate distances near a tile edge, which is a ring of missing offset lines
 * around every tile.
 *
 * ### Where the shoreline comes from
 *
 * Not from the biome raster. `Biome.OCEAN` is a per-kilometre-cell classification, so a contour traced
 * along it is a staircase with a 1 km tread - two pixels at world zoom and sixty at 16 m per pixel. The
 * shore here is the zero crossing of [shore], a continuous field, so it resolves at whatever zoom asks.
 *
 * The biome raster is still what colours the land, but as a *bilinear blend of its neighbours' tones*
 * rather than a nearest lookup, so an ecotone reads as a gradient instead of a cell wall. Water cells are
 * dropped from that blend and the weights renormalised - otherwise every coastal pixel is a quarter sea
 * colour and the whole coast goes muddy, which is the one thing the crisp [shore] contour exists to avoid.
 */
class TerrainRaster private constructor(
  val width: Int,
  val height: Int,

  /** Land surface in metres, bicubic. Defined everywhere, including under water. */
  val ground: DoubleArray,

  /**
   * Depth of the sea above the ground, negative on dry land, in metres. Simply `seaLevel - ground`.
   *
   * Smooth everywhere, which is the entire requirement: [Coastline] seeds its distance transform on this
   * field's sign changes, so a discontinuity anywhere in it becomes a spurious length of shoreline.
   *
   * ### Why lakes are not in here
   *
   * They were, and it was wrong in a way worth recording. Taking each pixel's water level from
   * `water_level` - nearest cell, dilated a cell outwards so the dry ring around a lake had a level to
   * measure against - gives a surface that is *piecewise constant on the kilometre grid*. Subtract a smooth
   * bicubic ground from it and every cell-to-cell change in level becomes a step, so the field acquires a
   * rectangular staircase wherever a lake or a river cell sat: an 8-pixel quilt over the land at world
   * zoom, and a stray rectangle around every lake where the dilation stopped.
   *
   * The sea needs none of it. `seaLevel` is one number for the whole world, so this difference is as smooth
   * as the heightfield, and standing water above sea level belongs to the vector pass, where a lake is an
   * `AreaFeature` with an exact ring and its own surface elevation on `LakeChannels`. That is both more
   * accurate and less code - the raster was approximating geometry that was already there.
   */
  val shore: DoubleArray,

  /** Biome tint per pixel, already blended and already washed with ice. */
  val landTone: IntArray
) {

  fun index(x: Int, y: Int): Int = (y + HALO) * width + (x + HALO)

  companion object {

    /** Sized by [Coastline.REACH_PIXELS], the widest neighbourhood any pass reads. */
    val HALO = ceil(Coastline.REACH_PIXELS).toInt() + 1

    /**
     * [shore] outside the world, where there is no ground to measure against.
     *
     * Unambiguously dry rather than zero: zero is the value the coastline tracer looks for, so a no-data
     * pixel at exactly zero would draw a shore along the edge of the world.
     */
    private const val NO_DATA_SHORE = -1.0

    fun sample(view: Viewport, inputs: TileInputs, palette: AtlasPalette): TerrainRaster {
      val w = view.widthPx + 2 * HALO
      val h = view.heightPx + 2 * HALO
      val n = w * h

      val ground = DoubleArray(n)
      val shore = DoubleArray(n)
      val landTone = IntArray(n)

      val elevation = inputs.elevation
      val seaLevel = inputs.seaLevel

      for (py in 0 until h) {
        val worldY = view.worldY(py - HALO)
        for (px in 0 until w) {
          val worldX = view.worldX(px - HALO)
          val i = py * w + px

          val g = if (inRange(elevation, worldX, worldY)) {
            elevation.sampleBicubic(worldX, worldY)
          } else {
            Double.NaN
          }
          ground[i] = g

          shore[i] = if (g.isNaN()) NO_DATA_SHORE else seaLevel - g

          landTone[i] = palette.landTone(
            blendedBiomeTone(inputs, palette, worldX, worldY),
            sampleOrZero(inputs.iceThickness, worldX, worldY)
          )
        }
      }

      return TerrainRaster(w, h, ground, shore, landTone)
    }

    /**
     * Bilinear blend of the four surrounding cells' land tones, water cells excluded.
     *
     * Interpolating the *colour* is legitimate where interpolating the ordinal is not: the result is a
     * tone between two land covers, which is what an ecotone looks like, rather than an ordinal that
     * denotes a third biome nobody classified.
     */
    private fun blendedBiomeTone(
      inputs: TileInputs,
      palette: AtlasPalette,
      worldX: Double,
      worldY: Double
    ): Int {
      val layer = inputs.biome
      val metresPerCell = layer.region.resolution.metresPerCell

      // Cell centres sit at (i + 0.5) cells, so shift by half before flooring to find the lower-left of
      // the four cells whose centres bracket this point.
      val fx = worldX / metresPerCell - 0.5
      val fy = worldY / metresPerCell - 0.5
      val x0 = floor(fx).toInt()
      val y0 = floor(fy).toInt()
      val tx = fx - x0
      val ty = fy - y0

      var r = 0.0
      var g = 0.0
      var b = 0.0
      var total = 0.0

      for (dy in 0..1) {
        for (dx in 0..1) {
          val x = x0 + dx
          val y = y0 + dy
          if (!layer.region.contains(x, y)) continue

          val cover = Biome.entries.getOrNull(layer[x, y]) ?: continue
          if (cover == Biome.OCEAN || cover == Biome.LAKE) continue

          val weight = (if (dx == 0) 1.0 - tx else tx) * (if (dy == 0) 1.0 - ty else ty)
          if (weight <= 0.0) continue

          val tone = palette.biomeTone(cover)
          r += weight * ((tone ushr 16) and 0xFF)
          g += weight * ((tone ushr 8) and 0xFF)
          b += weight * (tone and 0xFF)
          total += weight
        }
      }

      if (total <= 0.0) return palette.biomeTone(Biome.GRASSLAND)

      val inv = 1.0 / total
      return (((r * inv).toInt() and 0xFF) shl 16) or
          (((g * inv).toInt() and 0xFF) shl 8) or
          ((b * inv).toInt() and 0xFF)
    }

    private fun sampleOrZero(layer: FloatLayer, worldX: Double, worldY: Double): Double {
      if (!inRange(layer, worldX, worldY)) return 0.0
      val value = layer.sampleBilinear(worldX, worldY)
      return if (value.isNaN()) 0.0 else value
    }

    private fun inRange(layer: FloatLayer, worldX: Double, worldY: Double): Boolean {
      val metresPerCell = layer.region.resolution.metresPerCell
      return layer.region.contains(
        floor(worldX / metresPerCell).toInt(),
        floor(worldY / metresPerCell).toInt()
      )
    }
  }
}
