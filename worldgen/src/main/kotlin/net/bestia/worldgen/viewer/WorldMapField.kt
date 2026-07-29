package net.bestia.worldgen.viewer

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import kotlin.math.floor

/**
 * The whole world as one map: land cover, sea depth, lakes and ice, relief shaded, in one picture.
 *
 * Every other view in `viewer/` is diagnostic - one stage's output, isolated, so that a wrong value in it is
 * obvious. This is the opposite and is needed for the opposite reason: a field at a time will tell you that
 * precipitation is plausible and that biomes are plausible and never tell you that the world does not read as
 * a place. Being able to see it as a whole is what makes "there is a desert directly upwind of a rainforest"
 * or "that river runs the length of a continent and never meets another" something you notice.
 *
 * It also happens to be the view to open on, which is why [WorldScene] puts it first.
 *
 * ### Where each part of the picture comes from
 *
 * | On screen | Source |
 * |---|---|
 * | land colour | the biome raster, through [BiomePalette] |
 * | sea colour | elevation below sea level, through [Ramps.BATHYMETRY] - so a shelf and a trench differ |
 * | lake colour | `water_level` minus `elevation`, so a deep lake is a darker blue than a shallow one |
 * | ice | `ice_thickness` washed over whatever is underneath it |
 * | relief | hillshading, from [valueAt] |
 * | rivers, roads, settlements, coastlines, faults | the vector overlay, which every view already has |
 *
 * The last row is the reason this needed no new overlay code. Vector features were always drawn on top of
 * whichever field was showing; what was missing was a field worth drawing them on.
 *
 * ### The sea is flat
 *
 * [valueAt] returns the top of the *visible* surface - the water surface where there is standing water, the
 * ground where there is not - rather than the ground everywhere. That is what makes the sea shade flat and
 * the coastline crisp, instead of the seabed's relief showing through the water as though it were land.
 * Bathymetry is still in the picture; it is in the colour, where depth belongs, rather than in the shading,
 * where it would compete with the terrain.
 *
 * @param ice null when the pipeline has no glacial stage, in which case nothing is iced
 * @param water null when it has no hydrology, in which case the sea is the only standing water and it is
 *   flat by definition
 */
class WorldMapField(
  private val elevation: FloatLayer,
  private val biome: IntLayer,
  private val water: FloatLayer?,
  private val ice: FloatLayer?,
  private val seaLevel: Double,
  override val name: String = "world map",
  override val unit: String = "m"
) : CompositeField {

  /**
   * Only ever asked for its range and its [Palette.shadeable].
   *
   * A composite has no single colour scale, so there is no honest colour bar to draw and
   * [WorldViewPanel] does not draw one. The palette is still here because the renderer reads a range off
   * it for the status line, and elevation in metres is the right thing for that line to report.
   */
  override val palette: Palette = ElevationPalette(seaLevel)

  private val landCover = BiomePalette()

  /**
   * The top of the visible surface: standing water where there is any, ground where there is not.
   *
   * Bicubic on the ground, because that is what chunk generation lifts the terrain through, so the relief
   * here is the relief the client will get rather than a smoother or blockier version of it.
   */
  override fun valueAt(worldX: Double, worldY: Double): Double {
    val ground = groundAt(worldX, worldY)
    if (ground.isNaN()) return Double.NaN

    val surface = surfaceAt(worldX, worldY)
    return if (!surface.isNaN() && surface > ground) surface else ground
  }

  override fun rgbAt(worldX: Double, worldY: Double, value: Double): Int {
    val cover = biomeAt(worldX, worldY)

    // Sea depth through the bathymetry ramp. `value` is the water surface here, so the bed has to be
    // sampled again - the one place in this field where the flat-sea decision above costs something.
    if (cover == Biome.OCEAN) return palette.rgb(groundAt(worldX, worldY))

    if (cover == Biome.LAKE) {
      val depth = (value - groundAt(worldX, worldY)).coerceAtLeast(0.0)
      return Colors.mix(SHALLOW_LAKE, DEEP_LAKE, depth / FULL_TONE_DEPTH)
    }

    // Classified as land but below sea level: not a state the pipeline is meant to produce, so it is
    // shown as sea rather than hidden. An unclassified cell is likewise judged on its height alone.
    if (cover == null || value < seaLevel) return palette.rgb(value)

    val land = landCover.rgb(cover.ordinal.toDouble())
    val iceDepth = iceAt(worldX, worldY)

    // A wash rather than a replacement, so an ice field still shows what it is sitting on. The biome
    // classifier already calls the thickest ice GLACIER and ICE_SHEET and those are near-white anyway;
    // what this adds is the ice that lies over ground the classifier called tundra or alpine.
    return if (iceDepth <= 0.0) {
      land
    } else {
      Colors.mix(land, GLACIER_WHITE, MAX_ICE_WASH * (iceDepth / FULL_WASH_ICE).coerceAtMost(1.0))
    }
  }

  private fun groundAt(worldX: Double, worldY: Double): Double =
    if (contains(elevation, worldX, worldY)) elevation.sampleBicubic(worldX, worldY) else Double.NaN

  /**
   * NaN where there is no standing water; that is how the layer reports dry ground.
   *
   * Nearest cell, never interpolated, for the same reason a biome id is not interpolated: a water surface is
   * piecewise flat by nature - one level per body - and the value halfway between "water at 1002 m" and "no
   * water at all" does not exist. Interpolating it also propagated the dry cell's NaN into the last wet cell
   * before every coast (`NaN * 0.0` is `NaN`), so the seaward edge of every shoreline fell back to the
   * seabed's height and got a band of seabed relief shaded into the water.
   */
  private fun surfaceAt(worldX: Double, worldY: Double): Double {
    val layer = water ?: return Double.NaN
    val metresPerCell = layer.region.resolution.metresPerCell
    val cellX = floor(worldX / metresPerCell).toInt()
    val cellY = floor(worldY / metresPerCell).toInt()

    if (!layer.region.contains(cellX, cellY)) return Double.NaN
    return layer[cellX, cellY].toDouble()
  }

  private fun iceAt(worldX: Double, worldY: Double): Double {
    val layer = ice ?: return 0.0
    if (!contains(layer, worldX, worldY)) return 0.0
    val thickness = layer.sampleBilinear(worldX, worldY)
    return if (thickness.isNaN()) 0.0 else thickness
  }

  /** Nearest cell, never interpolated: the average of two biome ordinals is a third, unrelated biome. */
  private fun biomeAt(worldX: Double, worldY: Double): Biome? {
    val metresPerCell = biome.region.resolution.metresPerCell
    val cellX = floor(worldX / metresPerCell).toInt()
    val cellY = floor(worldY / metresPerCell).toInt()

    if (!biome.region.contains(cellX, cellY)) return null
    return Biome.of(biome[cellX, cellY])
  }

  private fun contains(layer: FloatLayer, worldX: Double, worldY: Double): Boolean {
    val metresPerCell = layer.region.resolution.metresPerCell
    return layer.region.contains(
      floor(worldX / metresPerCell).toInt(),
      floor(worldY / metresPerCell).toInt()
    )
  }

  private companion object {

    val SHALLOW_LAKE = Colors.rgb(96, 158, 206)
    val DEEP_LAKE = Colors.rgb(26, 66, 130)
    val GLACIER_WHITE = Colors.rgb(238, 246, 252)

    /** Lake depth at which the tone stops darkening. Deeper than most lakes a kilometre raster resolves. */
    const val FULL_TONE_DEPTH = 60.0

    /** Ice thickness at which the wash is at its strongest. */
    const val FULL_WASH_ICE = 120.0

    /** Ice never fully hides the ground under it - the point of a wash is that both are legible. */
    const val MAX_ICE_WASH = 0.85
  }
}
