package net.bestia.worldgen.viewer

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.climate.SeasonalPrecipitation
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.render.ColorRamp
import net.bestia.worldgen.render.Colors
import net.bestia.worldgen.render.Ramps
import net.bestia.worldgen.voxel.BlockType
import java.awt.Color

/**
 * Turns a field value into a pixel colour.
 *
 * Separate from [ColorRamp] because a ramp only knows `[0,1]`; a palette knows what the numbers
 * mean. That distinction is what lets elevation put a hard break at sea level while a generic
 * scalar just stretches across whatever range is present.
 */
interface Palette {

  fun rgb(value: Double): Int

  /** The value range this palette covers, or null to derive it from the data being rendered. */
  val range: ClosedFloatingPointRange<Double>?

  /** Hillshading a category map produces nonsense, so categorical palettes opt out. */
  val shadeable: Boolean get() = true

  /**
   * Whether the values this colours are labels rather than magnitudes.
   *
   * Drives the legend, and it has to be its own flag rather than being inferred. `shadeable` is the closest
   * existing signal and it is not the same question - [SurfaceOccupancyField]'s palette is continuous and
   * deliberately unshadeable - so reusing it would mislabel a real scale as a set of categories.
   *
   * What it prevents: a colour *bar* for a category map is nonsense end to end. The range comes from the 1st
   * and 99th percentile of the **ordinals** on screen, the ramp between two ordinals is a fiction, and the
   * numbers under it read `0 .. 21`. See [WorldViewPanel.drawLegend], which draws named swatches instead.
   */
  val categorical: Boolean get() = false

  /** A copy bound to a concrete range. Palettes with a fixed range may ignore it. */
  fun withRange(low: Double, high: Double): Palette
}

/** A plain ramp stretched across a value range. */
class ContinuousPalette(
  private val ramp: ColorRamp,
  override val range: ClosedFloatingPointRange<Double>? = null,
  override val shadeable: Boolean = true
) : Palette {

  override fun rgb(value: Double): Int {
    val r = range ?: return ramp.rgb(0.5)
    val span = r.endInclusive - r.start
    return ramp.rgb(if (span <= 0.0) 0.5 else (value - r.start) / span)
  }

  override fun withRange(low: Double, high: Double) = ContinuousPalette(ramp, low..high, shadeable)
}

/**
 * Elevation: bathymetry below sea level, hypsometric tints above, with the break exactly at sea
 * level rather than wherever a linear stretch happens to put it.
 *
 * Worth its own class because the coastline is the single most useful thing to be able to see. A
 * generic ramp makes an error of a few metres near zero invisible; this one turns it into a
 * visibly wrong shoreline.
 */
class ElevationPalette(
  private val seaLevel: Double = 0.0,
  private val deepest: Double = -4000.0,
  private val highest: Double = 4000.0
) : Palette {

  override val range get() = deepest..highest

  override fun rgb(value: Double): Int = if (value < seaLevel) {
    val span = seaLevel - deepest
    Ramps.BATHYMETRY.rgb(if (span <= 0.0) 1.0 else (value - deepest) / span)
  } else {
    val span = highest - seaLevel
    Ramps.HYPSOMETRIC_LAND.rgb(if (span <= 0.0) 0.0 else (value - seaLevel) / span)
  }

  /** Sea level is a property of the world, not of the view, so only the extremes move. */
  override fun withRange(low: Double, high: Double) =
    ElevationPalette(seaLevel, minOf(low, seaLevel), maxOf(high, seaLevel + 1.0))
}

/**
 * Discrete ids - plates, biomes, flow directions - coloured by a hash of the id.
 *
 * Hashing rather than a fixed table because ids are open-ended: a stage that starts emitting a new
 * biome id should get a new colour without anyone editing the viewer. Colours are stable across
 * runs because the hash is.
 */
class CategoryPalette(private val salt: Long = 0L) : Palette {

  override val range: ClosedFloatingPointRange<Double>? get() = null
  override val shadeable get() = false
  override val categorical get() = true

  override fun rgb(value: Double): Int {
    val id = value.toLong()
    val hue = GenRng.hashUnit(salt, id).toFloat()
    // Bounded saturation and brightness: fully random HSB produces neighbouring categories that are
    // indistinguishable on a dark background.
    val saturation = (0.45 + GenRng.hashUnit(salt, id, 1L) * 0.4).toFloat()
    val brightness = (0.55 + GenRng.hashUnit(salt, id, 2L) * 0.35).toFloat()
    return Color.HSBtoRGB(hue, saturation, brightness) and 0xFFFFFF
  }

  override fun withRange(low: Double, high: Double) = this
}

/**
 * Default palette per layer, so a stage added later shows up in the viewer with a sensible
 * colouring and no viewer change at all.
 */
object Palettes {

  fun forLayer(id: LayerId, seaLevel: Double = 0.0): Palette = when (id) {
    LayerId.ELEVATION, LayerId.BEDROCK_ELEVATION, LayerId.WATER_LEVEL -> ElevationPalette(seaLevel)

    LayerId.TEMPERATURE -> ContinuousPalette(Ramps.TEMPERATURE, -30.0..40.0)
    LayerId.TEMPERATURE_RANGE -> ContinuousPalette(Ramps.DIVERGING, 0.0..45.0)
    LayerId.PRECIPITATION -> ContinuousPalette(Ramps.PRECIPITATION, 0.0..4000.0)
    LayerId.PRECIPITATION_SEASONALITY -> ContinuousPalette(Ramps.DIVERGING, 0.0..1.0)

    /**
     * All four seasons on **one** range, which is the entire point of the arm existing.
     *
     * Without it they would still appear - the `else` below auto-ranges anything it has not been taught - and
     * each would auto-range independently, so four fields whose only purpose is being compared to each other
     * would be drawn to four different scales. A dry season and a monsoon would look alike and the wettest
     * quarter of the world would be indistinguishable from the driest.
     *
     * A quarter of [LayerId.PRECIPITATION]'s range, because a season is a quarter of a year: measured on the
     * reference world the seasonal fields run to a 99th percentile of about 3,500 mm against the annual
     * field's 14,000, so the same 1,000 mm is the same colour on both maps.
     */
    in SeasonalPrecipitation.LAYERS -> ContinuousPalette(Ramps.PRECIPITATION, 0.0..1000.0)

    LayerId.SOIL_FERTILITY, LayerId.HABITABILITY, LayerId.BIOME_CONFIDENCE, LayerId.RESOURCE_VALUE,
    LayerId.CANOPY_COVER ->
      ContinuousPalette(Ramps.VIRIDIS, 0.0..1.0)

    /**
     * Pinned to the unit range rather than auto-ranged, and for the seasonal fields' reason.
     *
     * Mana and the corruption derived from it are meant to be read against each other - the whole question
     * a designer asks of these two maps is "where did the towns hold it back" - and two auto-ranged fields
     * answer that in two different colour spaces. Corruption is also zero over most of the world by design,
     * which auto-ranging would stretch into a picture of the noise floor.
     *
     * [LayerId.VOLCANISM] is here for the second of those reasons rather than the first: it is exactly zero over
     * about sixty per cent of the land, and pinning is what keeps that reading as *absent* on the map. It is also
     * the map a designer sweeping the rarity knobs looks at, so the colour of "0.75" has to mean the same thing
     * from one run to the next or the sweep measures nothing.
     */
    LayerId.MANA_DENSITY, LayerId.CORRUPTION, LayerId.VOLCANISM ->
      ContinuousPalette(Ramps.VIRIDIS, 0.0..1.0)
    LayerId.SOIL_DEPTH -> ContinuousPalette(Ramps.VIRIDIS, 0.0..9.0)

    /** Ice is white where it is thick; a log-ish top end because an ice cap dwarfs a valley glacier. */
    LayerId.ICE_THICKNESS -> ContinuousPalette(Ramps.GRAYSCALE, 0.0..400.0)

    // Auto-ranged: the interesting structure in a cost field is the *contrast* between a valley and a
    // ridge, and a fixed range dominated by the impassable-water value would flatten all of it.
    LayerId.MOVEMENT_COST -> ContinuousPalette(Ramps.DIVERGING)

    LayerId.ROCK_HARDNESS -> ContinuousPalette(Ramps.GRAYSCALE, 0.0..1.0)
    LayerId.CRUST_AGE -> ContinuousPalette(Ramps.VIRIDIS, 0.0..1.0)

    // Auto-ranged rather than given a fixed span: uplift and sediment thickness both depend on the
    // erosion parameters, so any range hard-coded here would be wrong the first time those are tuned.
    LayerId.UPLIFT, LayerId.SEDIMENT, LayerId.DISTANCE_TO_OCEAN -> ContinuousPalette(Ramps.VIRIDIS)

    // Both span several orders of magnitude, so the *field* is expected to be log-scaled before it
    // reaches the palette; see LogScaledField.
    LayerId.FLOW_ACCUMULATION, LayerId.DISCHARGE -> ContinuousPalette(Ramps.PRECIPITATION)

    // Same palette as BIOME, so the two maps are directly comparable - which is the only way to read a
    // secondary map at all. The NO_SECONDARY sentinel is negative, so `COLORS.getOrNull` misses and it draws
    // in BiomePalette's hashed fallback colour: distinct from every real biome, which is what it needs to be.
    LayerId.BIOME, LayerId.BIOME_SECONDARY -> BiomePalette()
    LayerId.PLATE_ID, LayerId.LAKE_ID, LayerId.FLOW_DIRECTION -> CategoryPalette()

    else -> ContinuousPalette(Ramps.VIRIDIS)
  }
}

/**
 * What a category id in a layer *means*, in words. The companion to [Palettes], and deliberately its twin.
 *
 * An integer raster is a raster of labels, and reading `8` off the biome map tells you nothing you did not
 * already have to look up. Every one of these ids has a vocabulary somewhere in the generator; this is the one
 * place that knows which vocabulary belongs to which layer, so the cursor readout and the legend both say
 * `temperate forest`.
 *
 * Same shape as [Palettes.forLayer] on purpose, including the `else`: a layer nobody has taught this about
 * keeps showing its raw number rather than breaking, and a stage added tomorrow needs no viewer change.
 */
object Labels {

  /** Null when the layer's ids are genuinely just numbers. */
  fun forLayer(id: LayerId): ((Int) -> String?)? = when (id) {

    // `getOrNull` rather than an indexed read: an out-of-range ordinal here is a bad id
    // would confidently read as `cliff`. Falling back to the number is the honest answer, and it matches
    // how BiomePalette falls back to a hashed colour rather than inventing one.
    LayerId.BIOME -> { ordinal -> Biome.entries.getOrNull(ordinal)?.label }

    // `getOrNull` here too, and here it is load bearing rather than defensive: this layer *deliberately*
    // stores an out-of-range value, and a clamping reader would report every cell with no runner-up as a biome.
    LayerId.BIOME_SECONDARY -> { ordinal ->
      if (ordinal == LayerId.NO_SECONDARY) "no runner-up" else Biome.entries.getOrNull(ordinal)?.label
    }

    LayerId.FLOW_DIRECTION -> { d -> if (d == D8.NONE) "outflow" else D8.NAMES.getOrNull(d) }

    // Signed, and the sign is the information: a negative basin has no outlet to the sea, which is what
    // makes it a salt lake. See LayerId.LAKE_ID.
    LayerId.LAKE_ID -> { basin ->
      when {
        basin == 0 -> "none"
        basin < 0 -> "${-basin} (endorheic)"
        else -> basin.toString()
      }
    }

    // PLATE_ID has no vocabulary - a plate is only ever "the same one as over there" - so a number is
    // already the whole truth about it.
    else -> null
  }
}

/**
 * Biomes in colours that look like the thing they are.
 *
 * A hashed [CategoryPalette] would work and would be less code, but a biome map is one of the two or
 * three views you spend the most time reading, and reading it is far quicker when tundra is grey-green
 * and desert is sand-coloured than when they are whatever the hash produced. Unknown ordinals fall back
 * to the hash, so appending a biome cannot break the viewer.
 */
class BiomePalette : Palette {

  override val range: ClosedFloatingPointRange<Double>? get() = null
  override val shadeable get() = false
  override val categorical get() = true

  private val fallback = CategoryPalette(salt = 0xB10E5L)

  override fun rgb(value: Double): Int {
    if (value.isNaN()) return Colors.rgb(0, 0, 0)
    val ordinal = value.toInt()
    return COLORS.getOrNull(ordinal) ?: fallback.rgb(value)
  }

  override fun withRange(low: Double, high: Double) = this

  private companion object {
    /** Indexed by [net.bestia.worldgen.bio.Biome] ordinal; see the ordering warning on that enum. */
    val COLORS = intArrayOf(
      Colors.rgb(28, 62, 110),    // OCEAN
      Colors.rgb(48, 104, 172),   // LAKE
      Colors.rgb(238, 244, 250),  // ICE_SHEET
      Colors.rgb(150, 156, 132),  // TUNDRA
      Colors.rgb(60, 92, 66),     // TAIGA
      Colors.rgb(168, 160, 138),  // COLD_DESERT
      Colors.rgb(126, 124, 118),  // ALPINE
      Colors.rgb(72, 128, 62),    // TEMPERATE_FOREST
      Colors.rgb(40, 104, 72),    // TEMPERATE_RAINFOREST
      Colors.rgb(140, 176, 92),   // GRASSLAND
      Colors.rgb(178, 168, 104),  // DRYLAND
      Colors.rgb(224, 202, 138),  // DESERT
      Colors.rgb(92, 146, 60),    // TROPICAL_SEASONAL_FOREST
      Colors.rgb(26, 110, 48),    // TROPICAL_RAINFOREST
      // The two wetlands, and they have to be told apart at a glance because whether the split lands sensibly
      // on latitude is the thing this map is read to check. Bog is cold brown peat, swamp is dark green water.
      Colors.rgb(120, 104, 78),   // BOG
      Colors.rgb(52, 96, 84),     // SWAMP
      Colors.rgb(96, 168, 112),   // RIPARIAN
      Colors.rgb(232, 220, 176),  // BEACH
      Colors.rgb(166, 118, 82),   // BADLANDS
      // Near-black basalt, so a volcanic field reads as a hole in whatever it interrupts - which on a biome map
      // is exactly the right impression, and the fastest way to check the rarity by eye.
      Colors.rgb(44, 40, 44),     // VOLCANIC_FIELD
      // Sulfur yellow over grey sinter. Deliberately loud against the field beside it: the pair is meant to be
      // legible as a pair, since whether the basin lands in the valleys rather than as a ring around the cone is
      // the thing this map is read to find out.
      Colors.rgb(184, 168, 96)    // GEOTHERMAL_BASIN
    )
  }
}

/**
 * The block palette as map colours, for looking at what materialisation actually produced.
 *
 * This is the view that catches materialisation bugs the height views cannot: soil where there should be
 * bedrock, water on a hillside, a beach that wrapped the wrong side of a coast. Height is continuous and
 * hides those; block type does not.
 */
class BlockPalette : Palette {

  override val range: ClosedFloatingPointRange<Double>? get() = null
  override val shadeable get() = false
  override val categorical get() = true

  private val fallback = CategoryPalette(salt = 0xB10CL)

  override fun rgb(value: Double): Int {
    if (value.isNaN()) return Colors.rgb(0, 0, 0)
    return COLORS[value.toInt()] ?: fallback.rgb(value)
  }

  override fun withRange(low: Double, high: Double) = this

  private companion object {
    val COLORS: Map<Int, Int> = buildMap {
      put(BlockType.AIR.id, Colors.rgb(18, 18, 22))
      put(BlockType.WATER.id, Colors.rgb(44, 96, 168))
      put(BlockType.ICE.id, Colors.rgb(214, 234, 248))
      put(BlockType.LAVA.id, Colors.rgb(240, 92, 24))
      put(BlockType.GRANITE.id, Colors.rgb(178, 166, 160))
      put(BlockType.BASALT.id, Colors.rgb(78, 76, 80))
      put(BlockType.OBSIDIAN.id, Colors.rgb(26, 24, 32))
      put(BlockType.STONE.id, Colors.rgb(150, 146, 138))
      put(BlockType.LIMESTONE.id, Colors.rgb(214, 210, 190))
      put(BlockType.GRAVEL.id, Colors.rgb(146, 142, 136))
      put(BlockType.SAND.id, Colors.rgb(232, 214, 164))
      put(BlockType.DIRT.id, Colors.rgb(122, 92, 62))
      put(BlockType.MUD.id, Colors.rgb(74, 60, 44))
      put(BlockType.GRASS.id, Colors.rgb(96, 146, 72))
      put(BlockType.DRY_GRASS.id, Colors.rgb(178, 168, 104))
      put(BlockType.SNOW.id, Colors.rgb(246, 248, 252))

      // The blighted covers, which had no entries at all and fell through to the hashed fallback - so a
      // corrupted province came out in three arbitrary colours that changed nothing but looked deliberate.
      // Purple, because that is what corruption reads as everywhere else in the viewer.
      put(BlockType.BLIGHTED_GRASS.id, Colors.rgb(96, 72, 108))
      put(BlockType.BLIGHTED_DIRT.id, Colors.rgb(78, 60, 82))
      put(BlockType.BLIGHTED_SAND.id, Colors.rgb(150, 128, 148))

      put(BlockType.MASONRY.id, Colors.rgb(168, 160, 148))
      put(BlockType.COBBLESTONE.id, Colors.rgb(107, 105, 102))

      // Ore, one hue per metal and three steps of it. The steps are what a slice through a body has to show:
      // the point of grading ore was that the dense middle is worth more than the rim, and a single colour
      // per metal would draw the two identically.
      graded(BlockType.ORE_COPPER_SMALL, 122, 106, 92, 186, 118, 74)
      graded(BlockType.ORE_TIN_SMALL, 120, 122, 126, 194, 202, 212)
      graded(BlockType.ORE_IRON_SMALL, 116, 96, 88, 178, 104, 78)
      graded(BlockType.ORE_GOLD_SMALL, 138, 124, 88, 248, 206, 78)
      graded(BlockType.ORE_SILVER_SMALL, 132, 136, 140, 226, 232, 240)
      graded(BlockType.ORE_MITHRANDIUM_SMALL, 96, 116, 128, 122, 226, 240)
      graded(BlockType.ROCK_SALT_SMALL, 176, 172, 168, 244, 240, 236)
      // Acid yellow, pulled green away from gold's warm 248/206/78 so the two are told apart in a section.
      graded(BlockType.ORE_SULFUR_SMALL, 132, 130, 86, 224, 238, 66)
      // Magenta. Had no entry at all and fell through to the hashed fallback, which drew corrupted ore in a
      // colour with no relation to the corruption everything else in the viewer draws purple.
      graded(BlockType.ORE_AETHERITE_SMALL, 88, 64, 92, 226, 70, 214)

      // The gems. Each takes a hue no metal occupies, because the whole reason to look at a section is to
      // tell one body from another.
      //
      // Rose, and specifically not lava's orange, since pyrelith sits in basalt.
      graded(BlockType.GEM_PYRELITH_SMALL, 114, 56, 76, 244, 76, 132)
      // Blue-violet, kept off aetherite's magenta above.
      graded(BlockType.GEM_AMETHYST_SMALL, 104, 92, 132, 152, 108, 224)
      graded(BlockType.GEM_EMERALD_SMALL, 72, 104, 88, 48, 220, 130)
      graded(BlockType.GEM_RUBY_SMALL, 118, 62, 62, 232, 40, 56)
      // Near-white with a blue cast. The one gem whose bright end risks reading as snow, which is why its
      // dull end starts well down in grey rather than at rock's tint.
      graded(BlockType.GEM_DIAMOND_SMALL, 150, 158, 166, 232, 248, 255)
    }

    /**
     * The three grade blocks of one ore, ramped from a dull host-rock tint to the saturated colour.
     *
     * Takes the SMALL block and steps forward by id, which the palette layout guarantees is MEDIUM then RICH.
     */
    private fun MutableMap<Int, Int>.graded(
      small: BlockType,
      dullRed: Int, dullGreen: Int, dullBlue: Int,
      brightRed: Int, brightGreen: Int, brightBlue: Int
    ) {
      for (step in 0..2) {
        val t = step / 2.0
        put(
          small.id + step,
          Colors.rgb(
            (dullRed + (brightRed - dullRed) * t).toInt(),
            (dullGreen + (brightGreen - dullGreen) * t).toInt(),
            (dullBlue + (brightBlue - dullBlue) * t).toInt()
          )
        )
      }
    }
  }
}
