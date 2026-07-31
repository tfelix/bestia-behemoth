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
  private val boundaryJitter: Double = 420.0,

  /**
   * The runner-up biome and how clearly it lost, for the transition dither. Optional together: a partial
   * pipeline that stops before [net.bestia.worldgen.bio.BiomeStage] has neither, and the sampler then behaves
   * exactly as it did before the pair existed.
   */
  private val secondaryBiome: IntLayer? = null,
  private val biomeConfidence: FloatLayer? = null
) {

  private val jitterSeed = GenRng.mix64(seed xor JITTER_SALT)
  private val ditherSeed = GenRng.mix64(seed xor DITHER_SALT)

  /**
   * Which biome covers this column.
   *
   * Two mechanisms, and they do different jobs. The **warp** displaces the lookup position by a smooth noise
   * field, which makes the boundary itself irregular rather than a kilometre-long axis-aligned ruler edge.
   * The **dither** then lets the runner-up biome win individual columns near that boundary in proportion to
   * how narrowly it lost, which makes the two interpenetrate instead of meeting along a line - the difference
   * between a crisp coastline-shaped border and an ecotone.
   *
   * Dithering rather than blending, because these are categories: there is no biome half way between taiga and
   * tundra, and `BlockType` cannot be interpolated either. The classifier's own KDoc has always described this
   * as the intended use of the confidence value.
   *
   * **Coherent patches, not a per-column coin flip, and this was measured rather than chosen.** The first
   * implementation hashed the quantised world position exactly as the ruin rubble scatter does - seam-free,
   * exactly proportional, and wrong to look at. `probe -Px=38500 -Py=48500` on the reference world went from a
   * uniform 48 m patch of gravel to a 50/50 checkerboard of gravel and grass over the whole window, every test
   * still passing. At a metre per voxel that reads as display noise rather than as ground.
   *
   * The cause is that **[LayerId.BIOME_CONFIDENCE] is not calibrated to be a blend weight**, which the
   * classifier's KDoc claim of one "for free" does not lead you to expect. Measured over the cells that have a
   * runner-up at all, it is 0.066 at the median and 0.343 at the 95th percentile: with fourteen prototypes in
   * seven dimensions the nearest and second-nearest distances concentrate, so `1 - sqrt(best/second)` sits near
   * zero nearly everywhere. It ranks cells by how transitional they are perfectly well; its absolute scale is
   * not a mixing fraction, and using it as one dithers the entire world at close to even odds.
   *
   * So two changes. A [DITHER_CUTOFF] above which nothing mixes at all, which confines the effect to genuinely
   * near-tied ground instead of to the whole map; and a smooth noise field in place of the hash, so what mixing
   * remains arrives as patches a few metres across - the shape a real ecotone has - rather than as speckle. The
   * field is a pure function of world position, so the seam-free property the hash had is kept: two chunks
   * either side of a border evaluate the same noise and agree column for column. That property is what the
   * chunk seam check cannot catch, since it compares heights rather than blocks.
   *
   * Area fractions of the runner-up, measured on the patch field against the cutoff (see [PATCH_WAVELENGTH]):
   * a perfect tie gives half the ground, the median cell about 14%, the 75th percentile under 1%, and anything
   * at or above the cutoff none. Half at a perfect tie is the one exact point in it - two prototypes that
   * scored identically have equal claim, and neither has a claim on *more* than half.
   *
   * Recalibrating `BIOME_CONFIDENCE` itself - to a percentile rank, say - would be the better fix and is a
   * `BiomeStage` retune: seven stages read biomes downstream. It belongs in a change that measures them.
   */
  fun biomeAt(worldX: Double, worldY: Double): Biome {
    val displaced = Noise.warp(
      jitterSeed, worldX, worldY, boundaryJitter, 1.0 / JITTER_WAVELENGTH, JITTER_OCTAVES
    )
    val winner = Biome.of(biome.sampleNearest(displaced[0], displaced[1]))

    val secondary = secondaryBiome ?: return winner
    val confidence = biomeConfidence ?: return winner

    // `getOrNull`, not `Biome.of`: `of` coerces, so the NO_SECONDARY sentinel would come back as the last
    // enum entry and every cell without a runner-up would be a candidate to become a cliff.
    val runnerUp = Biome.entries.getOrNull(secondary.sampleNearest(displaced[0], displaced[1]))
      ?: return winner

    // Bilinear on the confidence, so the mixing zone's *width* varies smoothly rather than stepping at the
    // kilometre grid - a dither whose probability is a staircase draws the staircase.
    val clarity = confidence.sampleBilinear(displaced[0], displaced[1]).coerceIn(0.0, 1.0)
    val ambiguity = (1.0 - clarity / DITHER_CUTOFF).coerceIn(0.0, 1.0)
    if (ambiguity <= 0.0) return winner

    // Half the field lies below its own midpoint, so an ambiguity of 1 - a dead tie - splits the ground evenly.
    // Below that the area falls away faster than the threshold does, which is what keeps the mixing local.
    val threshold = 0.5 * ambiguity
    val patch = (Noise.fbm(ditherSeed, worldX / PATCH_WAVELENGTH, worldY / PATCH_WAVELENGTH, 1) + 1.0) / 2.0

    return if (patch < threshold) runnerUp else winner
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

  companion object {
    private const val JITTER_SALT = 0x2A6C1F953D8E470L
    private const val JITTER_WAVELENGTH = 1_100.0
    private const val JITTER_OCTAVES = 2

    /** Separate from [JITTER_SALT] so the dither is independent of the warp rather than correlated with it. */
    private const val DITHER_SALT = 0x24B7E0C3A159D8L

    /**
     * Biome confidence at or above which no mixing happens at all.
     *
     * 0.2 sits between the 75th and 95th percentile of the measured confidence distribution, so the mixing is
     * confined to roughly the most ambiguous quarter of the world rather than applying to all of it. Without a
     * cutoff the effect is unbounded above by anything except the confidence layer's own compressed scale -
     * see [biomeAt] for the measurement and why that scale cannot be used directly.
     */
    const val DITHER_CUTOFF = 0.2

    /**
     * Patch size of the mixing field in metres, near enough.
     *
     * Chosen for how it looks at a metre per voxel: blobs of about half this across, which is the scale a
     * patch of heath in grassland actually has. The *area* fractions in [biomeAt] do not depend on this - a
     * stationary noise field has the same value distribution at any wavelength - so this is purely the visual
     * grain and can be retuned without moving any of them.
     */
    const val PATCH_WAVELENGTH = 14.0
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
