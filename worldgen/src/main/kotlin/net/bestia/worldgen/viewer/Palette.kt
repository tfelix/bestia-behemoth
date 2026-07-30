package net.bestia.worldgen.viewer

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
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

    LayerId.SOIL_FERTILITY, LayerId.HABITABILITY, LayerId.BIOME_CONFIDENCE, LayerId.RESOURCE_VALUE ->
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

    LayerId.BIOME -> BiomePalette()
    LayerId.PLATE_ID, LayerId.LAKE_ID, LayerId.FLOW_DIRECTION -> CategoryPalette()

    else -> ContinuousPalette(Ramps.VIRIDIS)
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
      Colors.rgb(206, 226, 240),  // GLACIER
      Colors.rgb(150, 156, 132),  // TUNDRA
      Colors.rgb(60, 92, 66),     // TAIGA
      Colors.rgb(168, 160, 138),  // COLD_DESERT
      Colors.rgb(126, 124, 118),  // ALPINE
      Colors.rgb(72, 128, 62),    // TEMPERATE_FOREST
      Colors.rgb(40, 104, 72),    // TEMPERATE_RAINFOREST
      Colors.rgb(140, 176, 92),   // GRASSLAND
      Colors.rgb(178, 172, 110),  // STEPPE
      Colors.rgb(158, 148, 92),   // SHRUBLAND
      Colors.rgb(224, 202, 138),  // DESERT
      Colors.rgb(196, 186, 96),   // SAVANNA
      Colors.rgb(92, 146, 60),    // TROPICAL_SEASONAL_FOREST
      Colors.rgb(26, 110, 48),    // TROPICAL_RAINFOREST
      Colors.rgb(74, 116, 108),   // WETLAND
      Colors.rgb(96, 168, 112),   // RIPARIAN
      Colors.rgb(232, 220, 176),  // BEACH
      Colors.rgb(166, 118, 82),   // BADLANDS
      Colors.rgb(112, 108, 104)   // CLIFF
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
      put(BlockType.GRANITE.id, Colors.rgb(178, 166, 160))
      put(BlockType.BASALT.id, Colors.rgb(78, 76, 80))
      put(BlockType.LIMESTONE.id, Colors.rgb(214, 210, 190))
      put(BlockType.SANDSTONE.id, Colors.rgb(206, 172, 122))
      put(BlockType.SHALE.id, Colors.rgb(104, 104, 112))
      put(BlockType.CONGLOMERATE.id, Colors.rgb(158, 140, 118))
      put(BlockType.GRAVEL.id, Colors.rgb(146, 142, 136))
      put(BlockType.SAND.id, Colors.rgb(232, 214, 164))
      put(BlockType.CLAY.id, Colors.rgb(160, 128, 106))
      put(BlockType.DIRT.id, Colors.rgb(122, 92, 62))
      put(BlockType.PEAT.id, Colors.rgb(74, 60, 44))
      put(BlockType.PERMAFROST.id, Colors.rgb(152, 160, 168))
      put(BlockType.GRASS.id, Colors.rgb(96, 146, 72))
      put(BlockType.SNOW.id, Colors.rgb(246, 248, 252))
      put(BlockType.ORE_COPPER.id, Colors.rgb(186, 118, 74))
      put(BlockType.ORE_TIN.id, Colors.rgb(178, 186, 196))
      put(BlockType.ORE_IRON.id, Colors.rgb(150, 106, 90))
      put(BlockType.ORE_GOLD.id, Colors.rgb(232, 196, 84))
      put(BlockType.ORE_SILVER.id, Colors.rgb(214, 220, 228))
      put(BlockType.COAL_SEAM.id, Colors.rgb(44, 42, 46))
      put(BlockType.ROCK_SALT.id, Colors.rgb(238, 232, 226))
      put(BlockType.MASONRY.id, Colors.rgb(168, 160, 148))
      put(BlockType.TIMBER.id, Colors.rgb(112, 79, 48))
      put(BlockType.PLASTER.id, Colors.rgb(220, 210, 186))
      put(BlockType.THATCH.id, Colors.rgb(184, 153, 76))
      put(BlockType.ROOF_TILE.id, Colors.rgb(140, 66, 51))
      put(BlockType.PLANK.id, Colors.rgb(153, 115, 71))
      put(BlockType.RUBBLE.id, Colors.rgb(122, 118, 110))
      put(BlockType.COBBLESTONE.id, Colors.rgb(107, 105, 102))
    }
  }
}
