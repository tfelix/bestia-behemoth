package net.bestia.zone.cartography.render

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.render.Colors

/**
 * Every tone the atlas style uses, in one table.
 *
 * A drawn map is not a coloured photograph of the ground, and the difference is almost entirely in the
 * palette rather than in the drawing. Two rules produce the look, and both are choices this table exists to
 * make explicit:
 *
 * 1. **The ground is paper, not terrain.** Land and sea differ by a few percent of lightness, not by hue.
 *    All the contrast in the picture belongs to the ink - the coastline, the hatching, the glyphs - because
 *    that is where a reader's eye goes and what survives being printed, scaled and faded.
 * 2. **Biome is a stain, not a fill.** [biomeTone] varies over a narrow band around the paper tone.
 *    Saturate it and the result is a biome debug view with mountains drawn on it, which
 *    `viewer/WorldMapField` already does better and honestly.
 *
 * [MONOCHROME] is the same table with the hues taken out - the greyscale pen-and-ink look. Keeping it as a
 * second instance rather than a flag is what stops the tone decisions from being duplicated: a new biome
 * gets its lightness from [biomeTone] once and both palettes render it.
 */
class AtlasPalette(
  /** Bare paper, and what the sea is: the lightest thing on the map. */
  val paper: Int,

  /** The darkest ink, for coastlines and glyph outlines. */
  val ink: Int,

  /** Open water, a shade cooler and lighter than land so the coast reads without a heavy line. */
  val water: Int,

  /** Deep water, mixed towards from [water] by depth. */
  val waterDeep: Int,

  /** Bare land, before any biome stain. */
  val land: Int,

  /** Rivers and lakes: the same ink as the coast, so all water edges agree. */
  val waterInk: Int,

  /** Roads, which are drawn as a dashed line over everything else. */
  val roadInk: Int,

  /** Glacier and permanent snow, washed over whatever it covers. */
  val ice: Int,

  /** How far a biome stain may pull the land tone, 0 = no stain, 1 = full [biomeTone]. */
  val biomeStain: Double,

  /** Whether hues are kept. False collapses every tone onto its own luminance. */
  private val coloured: Boolean
) {

  /**
   * The land tone for a blended biome colour, washed with ice.
   *
   * A wash rather than a replacement, so an ice field still shows what it lies on - the same reasoning
   * `viewer/WorldMapField` gives, and the reason the biome classifier's own `ICE_SHEET` is not enough:
   * this also catches ice lying over ground called tundra or alpine.
   */
  fun landTone(biomeTone: Int, iceThicknessMetres: Double): Int {
    val stained = Colors.mix(land, biomeTone, biomeStain)
    if (iceThicknessMetres <= 0.0) return stained

    val wash = MAX_ICE_WASH * (iceThicknessMetres / FULL_WASH_ICE_METRES).coerceAtMost(1.0)
    return Colors.mix(stained, ice, wash)
  }

  /** The tone a biome stains its ground with, before [biomeStain] scales how much of it lands. */
  fun biomeTone(biome: Biome): Int {
    val tone = when (biome) {
      // Water is never stained onto land - TerrainRaster drops these from the blend - but the `when` is
      // exhaustive so that a new biome cannot be added without choosing a tone for it.
      Biome.OCEAN, Biome.LAKE -> water

      Biome.ICE_SHEET -> ice
      Biome.TUNDRA -> rgb(214, 214, 205)
      Biome.TAIGA -> rgb(178, 194, 172)
      Biome.COLD_DESERT -> rgb(216, 210, 194)
      Biome.ALPINE -> rgb(208, 204, 198)

      Biome.TEMPERATE_FOREST -> rgb(180, 196, 166)
      Biome.TEMPERATE_RAINFOREST -> rgb(170, 192, 164)
      Biome.GRASSLAND -> rgb(210, 208, 178)

      Biome.DRYLAND -> rgb(220, 206, 172)
      Biome.DESERT -> rgb(232, 216, 176)
      Biome.BADLANDS -> rgb(216, 196, 170)

      Biome.TROPICAL_SEASONAL_FOREST -> rgb(190, 198, 158)
      Biome.TROPICAL_RAINFOREST -> rgb(166, 190, 156)

      Biome.BOG -> rgb(190, 196, 178)
      Biome.SWAMP -> rgb(182, 194, 172)
      Biome.RIPARIAN -> rgb(186, 200, 172)
      Biome.BEACH -> rgb(232, 222, 194)

      Biome.VOLCANIC_FIELD -> rgb(198, 188, 184)
      Biome.GEOTHERMAL_BASIN -> rgb(206, 196, 186)
    }

    return if (coloured) tone else desaturate(tone)
  }

  private fun rgb(r: Int, g: Int, b: Int): Int = Colors.rgb(r, g, b)

  companion object {

    /** Ice thickness at which the wash is at its strongest. */
    private const val FULL_WASH_ICE_METRES = 120.0

    /** Ice never fully hides the ground under it - the point of a wash is that both stay legible. */
    private const val MAX_ICE_WASH = 0.8

    /**
     * Rec. 709 luma. Perceptual rather than a flat average, so [MONOCHROME] keeps the *relative* lightness
     * of the coloured table: a plain mean turns desert and taiga into the same grey.
     */
    private fun desaturate(rgb: Int): Int {
      val y = (0.2126 * Colors.red(rgb) + 0.7152 * Colors.green(rgb) + 0.0722 * Colors.blue(rgb)).toInt()
      return Colors.rgb(y, y, y)
    }

    /** Warm parchment and sepia ink. The default: recognisably a drawn map, still readable in colour. */
    val PARCHMENT = AtlasPalette(
      paper = Colors.rgb(240, 232, 212),
      ink = Colors.rgb(58, 46, 32),
      water = Colors.rgb(226, 226, 214),
      waterDeep = Colors.rgb(202, 208, 202),
      land = Colors.rgb(222, 214, 190),
      waterInk = Colors.rgb(92, 104, 112),
      roadInk = Colors.rgb(120, 96, 66),
      ice = Colors.rgb(244, 246, 246),
      biomeStain = 0.55,
      coloured = true
    )

    /** Pen and ink on grey stock, as in the reference plates. Same geometry, no hues. */
    val MONOCHROME = AtlasPalette(
      paper = Colors.rgb(246, 246, 244),
      ink = Colors.rgb(38, 38, 38),
      water = Colors.rgb(250, 250, 249),
      waterDeep = Colors.rgb(232, 232, 231),
      land = Colors.rgb(216, 216, 213),
      waterInk = Colors.rgb(72, 72, 72),
      roadInk = Colors.rgb(96, 96, 96),
      ice = Colors.rgb(252, 252, 252),
      biomeStain = 0.35,
      coloured = false
    )

    fun byName(name: String): AtlasPalette = when (name.lowercase()) {
      "parchment" -> PARCHMENT
      "mono", "monochrome" -> MONOCHROME
      else -> throw IllegalArgumentException("Unknown atlas palette '$name', expected parchment or mono")
    }
  }
}
