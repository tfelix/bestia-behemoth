package net.bestia.zone.cartography.render

import net.bestia.worldgen.render.Viewport

/**
 * The metre-scale half of the relief: what the kilometre raster cannot carry.
 *
 * `elevation` is smooth by construction, so at close range a hillside is a flat wash with nothing for
 * [InkRelief] to band. `base.heightAt` has the detail, but only its *difference* from the raster is wanted -
 * the raster already supplies the large-scale shape and adding the whole field would double it.
 *
 * ### Why the strength depends on the zoom
 *
 * The detail's amplitude is fixed in metres while a pixel shrinks as you zoom in, so its *gradient in pixels*
 * grows without bound: at 32 m per pixel it is a texture, and at 2 it is a cliff every pixel. Hillshading
 * reads gradients, so an unscaled detail term saturates the darkest band across the whole map at close range -
 * which is exactly what a town plan looked like before this scaled.
 *
 * Scaling linearly with `metresPerPixel` holds the pixel-space gradient roughly constant, so the same hillside
 * reads with the same weight at every zoom. That is the same reasoning `Hillshade` already applies by taking
 * its gradients in pixel space rather than in metres.
 *
 * Above [MAX_METRES_PER_PIXEL] it is switched off entirely rather than merely scaled down. One sample per pixel
 * of a metre-scale field at 512 metres per pixel is not a coarse version of that field; it is noise with the
 * same variance and none of the structure, and banding it produces a rash rather than relief.
 */
object DetailRelief {

  /** Per-pixel height to add to [TerrainRaster.ground], or null at zooms where it would be speckle. */
  fun of(view: Viewport, inputs: TileInputs, terrain: TerrainRaster): DoubleArray? {
    if (view.metresPerPixel > MAX_METRES_PER_PIXEL) return null

    val share = SHARE * (view.metresPerPixel / MAX_METRES_PER_PIXEL).coerceAtMost(1.0)
    val out = DoubleArray(terrain.ground.size)

    for (py in 0 until terrain.height) {
      val worldY = view.worldY(py - TerrainRaster.HALO)
      for (px in 0 until terrain.width) {
        val i = py * terrain.width + px
        if (terrain.ground[i].isNaN()) continue

        val worldX = view.worldX(px - TerrainRaster.HALO)
        out[i] = (inputs.baseHeight.heightAt(worldX, worldY) - terrain.ground[i]) * share
      }
    }

    return out
  }

  /** Zoom at and above which the detail field is not sampled at all. 32 m per pixel is one voxel chunk. */
  const val MAX_METRES_PER_PIXEL = 32.0

  /**
   * Share of the detail field's departure from the raster that reaches the shading at [MAX_METRES_PER_PIXEL].
   *
   * Well under one even there: the detail noise has metre-scale amplitude over metre-scale distances, so at
   * full strength its gradient dwarfs the terrain's and the hillsides disappear under their own texture.
   */
  private const val SHARE = 0.45
}
