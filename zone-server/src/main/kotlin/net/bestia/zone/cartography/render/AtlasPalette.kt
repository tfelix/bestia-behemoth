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

  /**
   * Closed canopy, mixed towards by cover so a wood reads as a darker ground before a single tree is drawn.
   *
   * The biome tone cannot do this job. Cover varies continuously *inside* one biome - a temperate forest cell
   * is anywhere from half open to closed - and it is that variation, not the classification, that a reader
   * sees as the shape of a wood. Staining by biome alone paints the whole class one green and the forest has
   * no edge.
   */
  val forest: Int,

  /**
   * A single tree's crown: a darker, greener green than the [forest] ground it stands on.
   *
   * Darker than its ground, which is the opposite of how it started. A crown lighter than the wood it sits in
   * disappears into it - the outline is doing all the work and a hundred rings of outline is a texture, not a
   * forest. Inverting the two makes each crown a shape, and the ground was lightened at the same time so the
   * contrast comes from the pair rather than from driving either one to an extreme.
   */
  val crown: Int,

  /** How far a biome stain may pull the land tone, 0 = no stain, 1 = full [biomeTone]. */
  val biomeStain: Double,

  /**
   * How far a biome tone is pushed away from its own grey, before [biomeStain] decides how much of it lands.
   *
   * One leaves the table as written. Above one the tones are *extrapolated* away from neutral, which widens
   * the hue separation between biomes without editing twenty constants or touching their relative lightness -
   * so a second, more colourful palette is one number rather than a second table that would drift from this
   * one. Ignored when [coloured] is false, where the point is to have no hue at all.
   */
  private val chroma: Double = 1.0,

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
  fun landTone(biomeTone: Int, canopyCover: Double, iceThicknessMetres: Double): Int {
    val stained = Colors.mix(land, biomeTone, biomeStain)

    val shaded = if (canopyCover <= 0.0) {
      stained
    } else {
      Colors.mix(stained, forest, MAX_CANOPY_SHADE * (canopyCover / FULL_CANOPY_COVER).coerceAtMost(1.0))
    }

    // Ice last, so a glacier covers the wood it overran rather than being tinted by it.
    if (iceThicknessMetres <= 0.0) return shaded

    val wash = MAX_ICE_WASH * (iceThicknessMetres / FULL_WASH_ICE_METRES).coerceAtMost(1.0)
    return Colors.mix(shaded, ice, wash)
  }

  /** The tone a biome stains its ground with, before [biomeStain] scales how much of it lands. */
  fun biomeTone(biome: Biome): Int {
    val tone = when (biome) {
      // Water is never stained onto land - TerrainRaster drops these from the blend - but the `when` is
      // exhaustive so that a new biome cannot be added without choosing a tone for it.
      Biome.OCEAN, Biome.LAKE -> water

      Biome.ICE_SHEET -> ice
      // Cool rather than warm, and the same for ALPINE below. Both were a hair to the red side of neutral,
      // which is invisible in the restrained palette and comes out as pink once VIVID extrapolates it.
      Biome.TUNDRA -> rgb(206, 212, 210)
      Biome.TAIGA -> rgb(178, 194, 172)
      Biome.COLD_DESERT -> rgb(216, 210, 194)
      Biome.ALPINE -> rgb(196, 204, 214)

      Biome.TEMPERATE_FOREST -> rgb(180, 196, 166)
      Biome.TEMPERATE_RAINFOREST -> rgb(170, 192, 164)
      Biome.GRASSLAND -> rgb(210, 208, 178)

      Biome.DRYLAND -> rgb(220, 206, 172)
      Biome.DESERT -> rgb(232, 216, 176)
      Biome.BADLANDS -> rgb(216, 196, 170)

      Biome.TROPICAL_SEASONAL_FOREST -> rgb(190, 198, 158)
      Biome.TROPICAL_RAINFOREST -> rgb(166, 190, 156)

      // A wetland is the darkest, dullest green on the map, and a bog the browner of the two. They used to
      // sit a shade off grassland, which made the one kind of ground a traveller most wants warning of the
      // hardest to see.
      Biome.BOG -> rgb(166, 166, 126)
      Biome.SWAMP -> rgb(140, 162, 114)
      Biome.RIPARIAN -> rgb(186, 200, 172)
      Biome.BEACH -> rgb(232, 222, 194)

      // Basalt: clearly the darkest ground on the map, but not black. Volcanism is what raises most of this
      // world's mountains, so a volcanic field is not a rarity tucked in a corner - it runs along whole
      // ranges, and at near-black it stopped being a tone and became a blot with a visible edge.
      Biome.VOLCANIC_FIELD -> rgb(138, 128, 124)
      Biome.GEOTHERMAL_BASIN -> rgb(180, 162, 148)
    }

    return if (coloured) saturate(tone, chroma) else desaturate(tone)
  }

  private fun rgb(r: Int, g: Int, b: Int): Int = Colors.rgb(r, g, b)

  companion object {

    /**
     * Cover at which the wood is as dark as it gets, and how far it may pull the ground.
     *
     * Well under a full canopy, because cover rarely approaches its own maximum - the densest rainforest cell
     * in a world sits near 0.9 and ordinary woodland is 0.4 to 0.6, so scaling against 1.0 leaves every
     * temperate wood looking half cleared. The same reasoning as `GlyphScatter.CANOPY_GAIN`, applied to tone.
     */
    private const val FULL_CANOPY_COVER = 0.72
    private const val MAX_CANOPY_SHADE = 0.62

    /** Ice thickness at which the wash is at its strongest. */
    private const val FULL_WASH_ICE_METRES = 120.0

    /** Ice never fully hides the ground under it - the point of a wash is that both stay legible. */
    private const val MAX_ICE_WASH = 0.8

    /**
     * Rec. 709 luma. Perceptual rather than a flat average, so [MONOCHROME] keeps the *relative* lightness
     * of the coloured table: a plain mean turns desert and taiga into the same grey.
     */
    /**
     * Pushes a tone away from the grey of the same luminance, keeping that luminance.
     *
     * Extrapolation rather than a mix, so a factor above one is meaningful. Clamped per channel, which is
     * what stops a strong factor from wrapping a near-saturated tone round to its opposite.
     */
    private fun saturate(rgb: Int, amount: Double): Int {
      if (amount == 1.0) return rgb

      val grey = luma(rgb)
      return Colors.rgb(
        (grey + (Colors.red(rgb) - grey) * amount).toInt(),
        (grey + (Colors.green(rgb) - grey) * amount).toInt(),
        (grey + (Colors.blue(rgb) - grey) * amount).toInt()
      )
    }

    private fun luma(rgb: Int): Int =
      (0.2126 * Colors.red(rgb) + 0.7152 * Colors.green(rgb) + 0.0722 * Colors.blue(rgb)).toInt()

    private fun desaturate(rgb: Int): Int {
      val y = luma(rgb)
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
      forest = Colors.rgb(150, 170, 128),
      crown = Colors.rgb(118, 148, 90),
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
      forest = Colors.rgb(186, 186, 184),
      crown = Colors.rgb(150, 150, 148),
      biomeStain = 0.35,
      coloured = false
    )

    /**
     * The same tones pushed well away from neutral, and stained harder into the ground.
     *
     * For the question "which biome am I looking at", which the restrained palette answers only for a reader
     * who already knows the map. It is the deliberate opposite of [PARCHMENT]'s first rule - here the ground
     * does carry information rather than leaving all of it to the ink - so the two are kept as alternatives
     * rather than one being retuned into the other.
     */
    val VIVID = AtlasPalette(
      paper = Colors.rgb(240, 232, 212),
      ink = Colors.rgb(52, 42, 30),
      water = Colors.rgb(198, 216, 226),
      waterDeep = Colors.rgb(150, 180, 200),
      land = Colors.rgb(222, 214, 190),
      waterInk = Colors.rgb(74, 96, 116),
      roadInk = Colors.rgb(118, 92, 60),
      ice = Colors.rgb(246, 248, 250),
      forest = Colors.rgb(138, 168, 112),
      crown = Colors.rgb(96, 134, 66),
      biomeStain = 0.85,
      chroma = 2.1,
      coloured = true
    )

    fun byName(name: String): AtlasPalette = when (name.lowercase()) {
      "parchment" -> PARCHMENT
      "vivid" -> VIVID
      "mono", "monochrome" -> MONOCHROME
      else -> throw IllegalArgumentException("Unknown atlas palette '$name', expected parchment, vivid or mono")
    }
  }
}
