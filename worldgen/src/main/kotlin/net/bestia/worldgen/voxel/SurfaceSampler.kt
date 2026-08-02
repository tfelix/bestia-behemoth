package net.bestia.worldgen.voxel

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.LayerStore
import net.bestia.worldgen.core.ScopedLayerStore
import net.bestia.worldgen.core.WorldConfig
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
   * The fix was a smooth noise field in place of the hash, so the mixing arrives as patches a few metres across
   * - the shape a real ecotone has - rather than as speckle. The field is a pure function of world position, so
   * the seam-free property the hash had is kept: two chunks either side of a border evaluate the same noise and
   * agree column for column. That property is what the chunk seam check cannot catch, since it compares heights
   * rather than blocks.
   *
   * **The weight is a share because [LayerId.BIOME_CONFIDENCE] is a percentile rank**, which it was not when
   * this was written. The classifier's raw `1 - sqrt(best/second)` ranks transitional cells correctly and is not
   * a fraction of anything - fourteen prototypes in seven dimensions put the nearest and second-nearest
   * distances close together, so it measured 0.069 at the median and 0.361 at the 95th percentile. This method
   * compensated with a cutoff, and the compensation is gone: `BiomeStage` ranks the layer against the world's
   * own distribution, so `1 - clarity` is a share by construction and needs no rescaling. See
   * `BiomeStage.rankConfidence` for the measurement that killed the cutoff - at 0.2 it was excluding only 13%
   * of runner-up cells while handing the median cell 14% of its ground.
   *
   * Area fractions of the runner-up, measured with `probe --ecotone`: a perfect tie gives half the ground, the
   * median cell about 8%, and a confident cell effectively none. Half at a perfect tie is the one exact point in
   * it - two prototypes that scored identically have equal claim, and neither has a claim on *more* than half.
   */
  fun biomeAt(worldX: Double, worldY: Double): Biome {
    val displaced = Noise.warp(
      jitterSeed, worldX, worldY, boundaryJitter, 1.0 / JITTER_WAVELENGTH, JITTER_OCTAVES
    )
    val winner = Biome.entries[biome.sampleNearest(displaced[0], displaced[1])]

    val secondary = secondaryBiome ?: return winner
    val confidence = biomeConfidence ?: return winner

    // `getOrNull` and a null branch, not an indexed read: this layer carries the NO_SECONDARY sentinel for
    // a cell that had no runner-up, and there is nothing to blend towards there.
    val runnerUp = Biome.entries.getOrNull(secondary.sampleNearest(displaced[0], displaced[1]))
      ?: return winner

    // Bilinear on the confidence, so the mixing zone's *width* varies smoothly rather than stepping at the
    // kilometre grid - a dither whose probability is a staircase draws the staircase.
    val clarity = confidence.sampleBilinear(displaced[0], displaced[1]).coerceIn(0.0, 1.0)
    val ambiguity = 1.0 - clarity
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

    /**
     * The surface of a generated world, from the layers that decide it.
     *
     * A factory for the reason `Stratigraphy.of` is one, and the reason is now load bearing twice over:
     * `VegetationStage` rasterises the canopy from this classifier and the chunk tier plants the trees from
     * it, so the two agree about which ground is forest **only if both build the same object from the same
     * seven layers**. Two call sites each spelling out the list agree until one of them gains an eighth.
     */
    fun of(layers: LayerStore, config: WorldConfig) = SurfaceSampler(
      biome = layers.require(LayerId.BIOME),
      soilDepth = layers.require(LayerId.SOIL_DEPTH),
      waterLevel = layers.require(LayerId.WATER_LEVEL),
      lakeId = layers.require(LayerId.LAKE_ID),
      temperature = layers.require(LayerId.TEMPERATURE),
      seed = config.seed,
      seaLevel = config.seaLevel,
      // The pair that turns a biome boundary into an ecotone; see [biomeAt]. `require`, not an optional read:
      // if the biome stage has run at all it has emitted both, and a silent fallback here would mean the
      // dither quietly not happening.
      secondaryBiome = layers.require(LayerId.BIOME_SECONDARY),
      biomeConfidence = layers.require(LayerId.BIOME_CONFIDENCE)
    )

    /**
     * The same surface, for a stage rather than for the chunk tier.
     *
     * A second overload rather than a second call site, exactly as `Stratigraphy.of` has: the *list of
     * inputs* is what must stay in step and it is written once above. A stage reads through
     * [ScopedLayerStore], which refuses a layer the stage did not declare - so a caller must depend on
     * climate, hydrology and biomes or it fails loudly here rather than quietly reading a surface that is
     * not there.
     */
    fun of(layers: ScopedLayerStore, config: WorldConfig) = SurfaceSampler(
      biome = layers.int(LayerId.BIOME),
      soilDepth = layers.float(LayerId.SOIL_DEPTH),
      waterLevel = layers.float(LayerId.WATER_LEVEL),
      lakeId = layers.int(LayerId.LAKE_ID),
      temperature = layers.float(LayerId.TEMPERATURE),
      seed = config.seed,
      seaLevel = config.seaLevel,
      secondaryBiome = layers.int(LayerId.BIOME_SECONDARY),
      biomeConfidence = layers.float(LayerId.BIOME_CONFIDENCE)
    )

    private const val JITTER_SALT = 0x2A6C1F953D8E470L
    private const val JITTER_WAVELENGTH = 1_100.0
    private const val JITTER_OCTAVES = 2

    /** Separate from [JITTER_SALT] so the dither is independent of the warp rather than correlated with it. */
    private const val DITHER_SALT = 0x24B7E0C3A159D8L

    // There was a DITHER_CUTOFF here, and it is worth knowing why it is gone rather than only that it is.
    //
    // It existed to keep the mixing off the whole map, and its KDoc justified 0.2 by saying the value "sits
    // between the 75th and 95th percentile of the measured confidence distribution, so the mixing is confined
    // to roughly the most ambiguous quarter of the world". The percentile was read backwards: a cutoff above
    // the 75th percentile admits everything *below* it, which is seven eighths of the runner-up cells, not a
    // quarter. `probe --ecotone` says 87%.
    //
    // So it was never the bound it claimed to be, and once BIOME_CONFIDENCE became a percentile rank there was
    // nothing left for it to do: a rank is uniform, so `1 - rank` is already a share and the ramp falls off on
    // its own. See [biomeAt].

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

  /*
   * Both functions below dispatch on `when (biome)` with **no `else` branch**, and every biome is listed
   * even where the answer is the common one. That is deliberate and it is the whole reason they were
   * rewritten from a subject-less `when { biome == ... }` with a default at the bottom.
   *
   * A default here is an answer given on behalf of a biome nobody has thought about yet. The failure it
   * produces is not a crash or a blank patch - it is a plausible one. The next biome added to the enum gets
   * dirt under grass, looks entirely correct on the map, and is wrong in the one place nothing checks. The
   * precedent is `TownStructures`, whose `else -> SiteKind.MONUMENT` would have built every new kind of site
   * as a stone obelisk; and it matters more here than it did there, because vegetation now keys on the biome
   * too, so a silently-defaulted cover feeds a silently-defaulted forest.
   *
   * The cost is that adding a biome does not compile until somebody says what it is made of, which is the
   * point. Preserving the old ordering is why `cap` is two passes rather than one: the four bare-ground
   * biomes outranked the snow line and the rest did not.
   */

  /** The block filling the soil layer between bedrock and the surface. */
  fun soil(biome: Biome, temperature: Double): BlockType = when (biome) {
    Biome.WETLAND -> BlockType.PEAT
    Biome.BEACH, Biome.DESERT -> BlockType.SAND
    Biome.BADLANDS, Biome.RIPARIAN -> BlockType.CLAY

    // Ground that stays frozen year round is a different material to dig through, and saying so here
    // is cheaper than modelling it later. It applies only where the biome has not already named a soil.
    Biome.OCEAN, Biome.LAKE,
    Biome.ICE_SHEET, Biome.GLACIER, Biome.TUNDRA, Biome.TAIGA, Biome.COLD_DESERT, Biome.ALPINE,
    Biome.TEMPERATE_FOREST, Biome.TEMPERATE_RAINFOREST, Biome.GRASSLAND, Biome.STEPPE, Biome.SHRUBLAND,
    Biome.SAVANNA, Biome.TROPICAL_SEASONAL_FOREST, Biome.TROPICAL_RAINFOREST,
    Biome.CLIFF ->
      if (temperature < PERMAFROST_TEMPERATURE) BlockType.PERMAFROST else BlockType.DIRT
  }

  /**
   * The single topmost block of a column.
   *
   * @param waterDepth metres of water above the column, or 0 when it is dry land
   */
  fun cap(biome: Biome, temperature: Double, waterDepth: Double): BlockType {
    if (waterDepth > DEEP_WATER) return BlockType.CLAY
    if (waterDepth > 0.0) return if (temperature < 2.0) BlockType.GRAVEL else BlockType.SAND

    // Materials that outrank a snow cap. Ice already is frozen water, and the three bare-ground biomes are
    // bare because nothing settles on them - which is as true in a blizzard as it is in a drought.
    val bare = when (biome) {
      Biome.GLACIER, Biome.ICE_SHEET -> BlockType.ICE
      Biome.CLIFF -> BlockType.GRAVEL
      Biome.BADLANDS -> BlockType.CLAY
      Biome.BEACH, Biome.DESERT -> BlockType.SAND

      Biome.OCEAN, Biome.LAKE, Biome.TUNDRA, Biome.TAIGA, Biome.COLD_DESERT, Biome.ALPINE,
      Biome.TEMPERATE_FOREST, Biome.TEMPERATE_RAINFOREST, Biome.GRASSLAND, Biome.STEPPE, Biome.SHRUBLAND,
      Biome.SAVANNA, Biome.TROPICAL_SEASONAL_FOREST, Biome.TROPICAL_RAINFOREST,
      Biome.WETLAND, Biome.RIPARIAN -> null
    }
    if (bare != null) return bare

    if (temperature < SNOW_TEMPERATURE) return BlockType.SNOW

    return when (biome) {
      Biome.WETLAND -> BlockType.PEAT
      Biome.TUNDRA, Biome.COLD_DESERT -> BlockType.DIRT
      Biome.ALPINE -> BlockType.GRAVEL

      Biome.OCEAN, Biome.LAKE, Biome.TAIGA,
      Biome.TEMPERATE_FOREST, Biome.TEMPERATE_RAINFOREST, Biome.GRASSLAND, Biome.STEPPE, Biome.SHRUBLAND,
      Biome.SAVANNA, Biome.TROPICAL_SEASONAL_FOREST, Biome.TROPICAL_RAINFOREST, Biome.RIPARIAN,
      // Unreachable: the pass above returned for each of these. Listed so that the two `when`s stay
      // exhaustive over the same enum and a new biome is a compile error in both.
      Biome.GLACIER, Biome.ICE_SHEET, Biome.CLIFF, Biome.BADLANDS, Biome.BEACH, Biome.DESERT ->
        BlockType.GRASS
    }
  }

  /** Mean annual temperature below which the surface holds permanent snow. */
  private const val SNOW_TEMPERATURE = -1.5

  private const val PERMAFROST_TEMPERATURE = -3.0

  /** Water depth beyond which the bed is fine mud rather than sand. */
  private const val DEEP_WATER = 60.0
}
