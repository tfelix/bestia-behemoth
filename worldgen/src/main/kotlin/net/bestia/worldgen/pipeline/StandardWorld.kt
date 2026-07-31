package net.bestia.worldgen.pipeline

import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.BaseHeightField
import net.bestia.worldgen.core.ChunkColumnSource
import net.bestia.worldgen.core.ChunkHeightSampler
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageListener
import net.bestia.worldgen.core.World
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.core.WorldGenPipeline
import net.bestia.worldgen.civ.HabitabilityStage
import net.bestia.worldgen.civ.SettlementStage
import net.bestia.worldgen.civ.TownStage
import net.bestia.worldgen.history.HistoryStage
import net.bestia.worldgen.pop.EconomyStage
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.geo.GlacialStage
import net.bestia.worldgen.geo.TectonicsStage
import net.bestia.worldgen.geo.DropletHeightField
import net.bestia.worldgen.geo.DropletParams
import net.bestia.worldgen.geo.WorldHeightField
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.resource.ResourceStage
import net.bestia.worldgen.voxel.ChunkMaterializer
import net.bestia.worldgen.voxel.Stratigraphy
import net.bestia.worldgen.voxel.SurfaceSampler
import kotlin.math.min

/**
 * A generated world plus everything needed to materialise chunks from it.
 *
 * The world tier - rasters, vector features, and later the history log - is immutable and replicated;
 * the three samplers below it are stateless pure functions over that tier. Between them they are the
 * whole read-only half of the system, and none of them needs coordination with anything.
 */
class GeneratedWorld(
  val world: World,

  /** The continuous heightfield, before vector features. What features blend against. */
  val base: BaseHeightField,

  /** Terrain height per voxel column: base heightfield with every feature stamped. */
  val columns: ChunkColumnSource,

  /** Column heights turned into blocks. */
  val materializer: ChunkMaterializer
) {
  val config: WorldConfig get() = world.config
}

/**
 * The standard world pipeline.
 *
 * ```
 * tectonics -> climate -> erosion -> glacial
 *                              \-> hydrology -> biomes -> resources -> habitability
 *                                                                           |
 *                                        settlements -> history -> towns -> economy
 * ```
 *
 * Each stage declares only what it reads, and the scheduler enforces that, so this list is the entire
 * wiring - there is no order to get right here beyond the dependencies the stages already state.
 *
 * ### Why history runs before the towns it explains
 *
 * The build order numbers town layout 8 and history 10, and the dependencies run the other way: a town's
 * walls enclose the extent it had when it was threatened, its ruins are settlements history destroyed, how
 * much of it is stone follows the wealth history gave it, and how many buildings it has follows the
 * population history spent a thousand years deciding.
 *
 * History still does not *place* settlements - they are already where the land is good - which is the part
 * of the document's "retrofit" framing that mattered. What it does is date them, hold them, burn some and
 * empty others.
 *
 * ### What is implemented, and what is not
 *
 * The ledger lives in the **Implementation Status** section of `worldgen-architecture.md` and is not
 * duplicated here, because two copies of it drift. In short: build-order steps 1 to 11 are here, plus the
 * parts of 12 and 13 that belong in a module with no I/O in it; the service half of 12 is not.
 *
 * The eight deliberate deviations from the document are listed there too, and each is also noted at the
 * point in the code where it happens - [WorldHeightField] for analytic rather than droplet detail erosion,
 * [ErosionStage] for raster fans and deltas, `civ/StreetNetwork.kt` for plots that front streets rather than
 * subdividing blocks, and so on. A deviation visible in only one place is one somebody will later mistake
 * for a bug.
 */
object StandardWorld {

  /** Every stage of the world tier, in declaration order; the pipeline sorts them itself. */
  fun stages(config: WorldConfig): List<Stage> {
    val base = config.baseResolution
    return listOf(
      TectonicsStage(base),
      ClimateStage(climateResolutionFor(config)),
      ErosionStage(base),
      HydrologyStage(base),
      BiomeStage(base),
      GlacialStage(base),
      ResourceStage(base),
      HabitabilityStage(base),
      SettlementStage(base),
      HistoryStage(base),
      TownStage(base),
      EconomyStage(base)
    )
  }

  fun pipeline(config: WorldConfig) = WorldGenPipeline(stages(config))

  /**
   * Runs the world tier and assembles the chunk samplers on top of it.
   *
   * The stage graph is built from [config] rather than being a constant, because climate's resolution
   * depends on how big the world is - see [climateResolutionFor].
   */
  fun build(
    config: WorldConfig,
    listener: StageListener = StageListener.NONE,
    droplets: DropletParams = DropletParams()
  ): GeneratedWorld {
    val world = pipeline(config).generateWorld(config, listener)
    return assemble(world, droplets)
  }

  /**
   * The chunk tier for an already-generated world. Separate so a cached world tier can be reused.
   *
   * [droplets] is a **stage-style param rather than a `WorldConfig` field**, deliberately. A `WorldConfig`
   * field that decides terrain has to join `shapeVersion`'s explicit list and then `PersistedWorld`,
   * `WorldConfigMapping`, `WorldGenSettings.FLAGS` and `WorldArgs` all need it - four files and a database
   * column for a feature that ships off. A param keeps the decision at the call site, which is where the
   * one caller that turns it on lives.
   */
  fun assemble(world: World, droplets: DropletParams = DropletParams()): GeneratedWorld {
    val config = world.config
    val elevation = world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val hardness = world.layers.require<FloatLayer>(LayerId.ROCK_HARDNESS)

    val analytic = WorldHeightField(
      elevation = elevation,
      hardness = hardness,
      seed = config.seed,
      seaLevel = config.seaLevel
    )

    // Wrapped, not replaced: with droplets off this returns the analytic field's own value bit for bit, so the
    // default path is exactly what it was. See DropletHeightField for why the wrapper cannot introduce a seam.
    val base: BaseHeightField = if (droplets.enabled) {
      DropletHeightField(analytic, config.seed, droplets)
    } else {
      analytic
    }

    val columns = ChunkHeightSampler(config, base, world.features)

    val materializer = ChunkMaterializer(
      config = config,
      columns = columns,
      strata = Stratigraphy(
        coarseElevation = elevation,
        hardness = hardness,
        plateId = world.layers.require(LayerId.PLATE_ID),
        seed = config.seed,
        seaLevel = config.seaLevel
      ),
      surface = SurfaceSampler(
        biome = world.layers.require(LayerId.BIOME),
        soilDepth = world.layers.require(LayerId.SOIL_DEPTH),
        waterLevel = world.layers.require(LayerId.WATER_LEVEL),
        lakeId = world.layers.require(LayerId.LAKE_ID),
        temperature = world.layers.require(LayerId.TEMPERATURE),
        seed = config.seed,
        seaLevel = config.seaLevel,
        // The pair that turns a biome boundary into an ecotone; see SurfaceSampler.biomeAt. `require`, not an
        // optional read, because this assembles the full standard pipeline - if BiomeStage has run at all it
        // has emitted both, and a silent fallback here would mean the dither quietly not happening.
        secondaryBiome = world.layers.require(LayerId.BIOME_SECONDARY),
        biomeConfidence = world.layers.require(LayerId.BIOME_CONFIDENCE)
      ),
      features = world.features
    )

    return GeneratedWorld(world, base, columns, materializer)
  }

  /**
   * Climate runs four times coarser than the heightfield, unless coarsening would leave too few cells.
   *
   * Advection over a fine grid is wasted work - the process has a scale of hundreds of kilometres - but the
   * orographic sweep is a *march across cells*, so what it can express depends on how many cells there are to
   * march over. Below about a hundred, rain shadows have no room to develop: moisture is neither depleted
   * crossing a range nor recovered behind it, and the precipitation field comes out nearly flat, which in turn
   * leaves the biome classifier with one axis fewer to separate biomes on.
   *
   * The test is therefore on the *result* rather than on the input. A 128-cell world keeps full resolution and
   * gets 128 climate cells; a 512-cell world coarsens to 128; a 4096-cell world coarsens to 1024. Judging by
   * the input, as this did, sent a 128-cell world to a 32-cell climate grid - four times cheaper and much the
   * poorer for it, on a world that takes half a second to build anyway.
   */
  fun climateResolutionFor(config: WorldConfig): Resolution {
    val shortEdge = min(config.widthCells, config.heightCells)
    return if (shortEdge / CLIMATE_COARSENING >= MIN_CLIMATE_CELLS) {
      Resolution(config.baseResolution.metresPerCell * CLIMATE_COARSENING)
    } else {
      config.baseResolution
    }
  }

  /**
   * A world big enough to be interesting and small enough to generate while you watch.
   *
   * 512 km at kilometre cells is 262 144 cells: enough for several plates, a proper orographic rain
   * shadow, and river systems with fourth-order trunks, and it births in a few seconds. The 4096 km
   * world the architecture document sizes for is sixty-four times the work and belongs behind a
   * progress bar rather than in a viewer or a test.
   */
  fun demoConfig(seed: Long = DEFAULT_SEED) = WorldConfig(
    seed = seed,
    widthCells = 512,
    heightCells = 512,
    chunkSize = 32,
    voxelSize = 1.0
  )

  const val DEFAULT_SEED = 0xB3571AL

  private const val CLIMATE_COARSENING = 4.0

  /** Fewest climate cells across which the orographic sweep still produces a rain shadow worth having. */
  private const val MIN_CLIMATE_CELLS = 96
}
