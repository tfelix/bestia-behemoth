package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.fields.Noise

/**
 * The surface properties a column needs in order to be materialised: biome, soil depth, water level and
 * temperature, read from the world rasters at full world-space precision.
 *
 * The interesting part is [biomeAt]. Biome is a categorical raster on kilometre cells, and a nearest-cell
 * lookup would put dead straight, axis-aligned biome boundaries into the voxel world - a kilometre-long
 * ruler-straight edge between forest and desert, which is about the most artificial thing terrain can do.
 * Sampling at a position displaced by a smooth noise field breaks those edges into an irregular
 * transition without needing a blend between categories that cannot be blended, and it stays a pure
 * function of world position, so two chunks either side of a border still agree.
 */
class SurfaceSampler(
  private val biome: IntLayer,
  private val soilDepth: FloatLayer,
  private val waterLevel: FloatLayer,
  private val lakeId: IntLayer,
  private val temperature: FloatLayer,
  seed: Long,
  private val seaLevel: Double = 0.0,
  /** Amplitude of the biome-boundary dither in metres. Roughly how ragged a transition looks. */
  private val boundaryJitter: Double = 420.0
) {

  private val jitterSeed = GenRng.mix64(seed xor JITTER_SALT)

  fun biomeAt(worldX: Double, worldY: Double): Biome {
    val displaced = Noise.warp(
      jitterSeed, worldX, worldY, boundaryJitter, 1.0 / JITTER_WAVELENGTH, JITTER_OCTAVES
    )
    return Biome.of(biome.sampleNearest(displaced[0], displaced[1]))
  }

  fun soilDepthAt(worldX: Double, worldY: Double): Double =
    soilDepth.sampleBilinear(worldX, worldY).coerceAtLeast(0.0)

  fun temperatureAt(worldX: Double, worldY: Double): Double =
    temperature.sampleBilinear(worldX, worldY)

  /**
   * Elevation of the standing water surface over this column.
   *
   * Constant per water body rather than interpolated, and that is the point: a lake's surface is level by
   * definition, so the shoreline comes out of the *terrain* crossing a flat plane. Interpolating the
   * water level instead would make the shoreline follow the kilometre grid, and the lake would acquire a
   * visible staircase edge that no amount of terrain detail could hide.
   *
   * Sea level is the default everywhere else, which costs nothing: a column whose surface is above it
   * simply never fills.
   */
  fun waterLevelAt(worldX: Double, worldY: Double): Double {
    val lake = lakeId.sampleNearest(worldX, worldY)
    if (lake == 0) return seaLevel

    val level = waterLevel.sampleBilinear(worldX, worldY)
    return if (level.isNaN()) seaLevel else level
  }

  private companion object {
    const val JITTER_SALT = 0x2A6C1F953D8E470L
    const val JITTER_WAVELENGTH = 1_100.0
    const val JITTER_OCTAVES = 2
  }
}

/**
 * Which block a biome puts on top of its soil, and which soil it puts on top of its rock.
 *
 * Kept separate from the materialiser because it is a lookup table with opinions in it, and those are
 * exactly what a designer will want to change. It is the smallest of the several tables in this pipeline
 * that ought to end up in a data file.
 */
object SurfaceCover {

  /** The block filling the soil layer between bedrock and the surface. */
  fun soil(biome: Biome, temperature: Double): BlockType = when {
    biome == Biome.WETLAND -> BlockType.PEAT
    biome == Biome.BEACH || biome == Biome.DESERT -> BlockType.SAND
    biome == Biome.BADLANDS -> BlockType.CLAY
    biome == Biome.RIPARIAN -> BlockType.CLAY
    // Ground that stays frozen year round is a different material to dig through, and saying so here
    // is cheaper than modelling it later.
    temperature < PERMAFROST_TEMPERATURE -> BlockType.PERMAFROST
    else -> BlockType.DIRT
  }

  /**
   * The single topmost block of a column.
   *
   * @param waterDepth metres of water above the column, or 0 when it is dry land
   */
  fun cap(biome: Biome, temperature: Double, waterDepth: Double): BlockType = when {
    waterDepth > DEEP_WATER -> BlockType.CLAY
    waterDepth > 0.0 -> if (temperature < 2.0) BlockType.GRAVEL else BlockType.SAND

    biome == Biome.GLACIER || biome == Biome.ICE_SHEET -> BlockType.ICE
    biome == Biome.CLIFF -> BlockType.GRAVEL
    biome == Biome.BADLANDS -> BlockType.CLAY
    biome == Biome.BEACH -> BlockType.SAND
    biome == Biome.DESERT -> BlockType.SAND

    temperature < SNOW_TEMPERATURE -> BlockType.SNOW
    biome == Biome.WETLAND -> BlockType.PEAT
    biome == Biome.TUNDRA || biome == Biome.COLD_DESERT -> BlockType.DIRT
    biome == Biome.ALPINE -> BlockType.GRAVEL

    else -> BlockType.GRASS
  }

  /** Mean annual temperature below which the surface holds permanent snow. */
  private const val SNOW_TEMPERATURE = -1.5

  private const val PERMAFROST_TEMPERATURE = -3.0

  /** Water depth beyond which the bed is fine mud rather than sand. */
  private const val DEEP_WATER = 60.0
}
