package net.bestia.worldgen.pipeline

import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.bio.VegetationStage
import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.BaseHeightField
import net.bestia.worldgen.core.ChunkColumnSource
import net.bestia.worldgen.core.ChunkPos
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
import net.bestia.worldgen.civ.NavGraphStage
import net.bestia.worldgen.civ.SettlementStage
import net.bestia.worldgen.civ.TownStage
import net.bestia.worldgen.history.HistoryStage
import net.bestia.worldgen.pop.EconomyStage
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.geo.GlacialStage
import net.bestia.worldgen.geo.TectonicsStage
import net.bestia.worldgen.geo.VolcanismStage
import net.bestia.worldgen.geo.DropletHeightField
import net.bestia.worldgen.geo.DropletParams
import net.bestia.worldgen.geo.WorldHeightField
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.hydro.AlluviumStage
import net.bestia.worldgen.hydro.PondStage
import net.bestia.worldgen.karst.CaveChannels
import net.bestia.worldgen.karst.CaveStage
import net.bestia.worldgen.mana.CorruptionStage
import net.bestia.worldgen.mana.ManaStage
import net.bestia.worldgen.resource.ResourceStage
import net.bestia.worldgen.spawn.SpawnerStage
import net.bestia.worldgen.voxel.ChunkMaterializer
import net.bestia.worldgen.voxel.Stratigraphy
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.voxel.SurfaceSampler
import kotlin.math.max
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
  val materializer: ChunkMaterializer,

  /**
   * The tuning this world was generated with, resolved.
   *
   * Carried rather than discarded because a property check often needs the rule as well as the result:
   * `Invariants` can only say "these two deposits are closer than they should be" if it can read what the
   * dispersal pass was told to keep them.
   */
  val params: WorldParams = WorldParams.DEFAULT
) {
  val config: WorldConfig get() = world.config

  /**
   * Where the trees are, without materialising a chunk to find out.
   *
   * The only read path to vegetation that does not go through voxels, and the one an entity spawner wants:
   * `LayerId.CANOPY_COVER` says how wooded a square kilometre is, and this answers the same question at a
   * position - is there a trunk here, how dense is the wood around it. A tree is not a feature and never can
   * be, so without this the answer would only exist inside a generated chunk.
   */
  val vegetation get() = materializer.vegetation

  /**
   * The props standing in one chunk, for a runtime to turn into entities.
   *
   * This is the read path [vegetation] was documented as being for and never was - grep found no consumer
   * outside this module - so treat its shape as unproven until something outside actually reads it.
   *
   * See [ChunkMaterializer.propsIn] for the two things that matter: hand in the column heights if you
   * already have them, and never accumulate the result over a region.
   */
  fun propsIn(chunkX: Int, chunkY: Int) = materializer.propsIn(chunkX, chunkY)

  /**
   * Which vertical slabs of a horizontal chunk hold anything worth streaming.
   *
   * **The heightfield's span is no longer the answer, and that is what this exists to say.**
   * `ChunkService.computeSurfaceSlabs` subscribes a player to the slabs the terrain surface passes through,
   * which was complete while every block in the world was at the surface or under it. A cave forty metres
   * down can sit in the slab below, so it is generated, cached, and never sent - a player walks into a
   * passage and the ground in front of them does not exist.
   *
   * Answered as a **feature query plus two scans**, with nothing materialised. That is the whole reason a
   * passage stores its floor and its height on the feature rather than deriving them at chunk time: "is there
   * anything down there" is then a question about a few hundred polylines, not about a million voxels.
   *
   * Conservative in the safe direction. A passage that merely comes near the chunk contributes its whole
   * vertical extent, so the range can be a slab taller than it needs to be - which costs one empty chunk and
   * cannot lose one that has something in it.
   *
   * @return the inclusive range of `chunk.z` values to generate
   */
  fun contentSlabsOf(chunkX: Int, chunkY: Int): IntRange {
    val heights = columns.heights(ChunkPos(chunkX, chunkY), 0)

    var lowest = Double.MAX_VALUE
    var highest = -Double.MAX_VALUE
    for (localY in 0 until config.chunkSize) {
      for (localX in 0 until config.chunkSize) {
        val h = heights[localX, localY]
        if (h < lowest) lowest = h
        if (h > highest) highest = h
      }
    }

    // One voxel of headroom below the lowest column, the same allowance `materializeSurface` makes and for the
    // same reason: a column within half a voxel of a slab floor has its surface voxel in the slab beneath.
    var bottom = config.chunkZOf(lowest - config.voxelSize)
    var top = config.chunkZOf(highest)

    val bounds = config.chunkBounds(ChunkPos(chunkX, chunkY))
    for (feature in world.features.query(bounds)) {
      if (feature.kind != FeatureKind.CAVE_PASSAGE) continue
      val stations = feature as? MarkerFeature ?: continue
      val table = stations.stations ?: continue
      val floorChannel = runCatching { table.channel(CaveChannels.FLOOR) }.getOrNull() ?: continue
      val heightChannel = table.channel(CaveChannels.HEIGHT)

      for (i in 0 until table.stationCount) {
        val floor = table.valueAt(floorChannel, i)
        bottom = min(bottom, config.chunkZOf(floor - config.voxelSize))
        top = max(top, config.chunkZOf(floor + table.valueAt(heightChannel, i)))
      }
    }

    return bottom..top
  }
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

  /**
   * Every stage of the world tier, in declaration order; the pipeline sorts them itself.
   *
   * The one place the twelve stages are constructed, and therefore the one place tuning has to be threaded.
   * Every stage has always taken a params object and no caller ever supplied one, so this list is where a
   * params file becomes a world - see [WorldParams], whose `resolved` form is used rather than its declared
   * one so that a value shared by two stages is forwarded rather than defaulted twice.
   */
  fun stages(config: WorldConfig, params: WorldParams = WorldParams.DEFAULT): List<Stage> {
    val base = config.baseResolution
    val p = params.resolved
    return listOf(
      TectonicsStage(base, p.tectonics),
      ClimateStage(climateResolutionFor(config), p.climate),
      ErosionStage(base, p.erosion),
      HydrologyStage(base, p.hydrology),
      // Before the biomes on a real edge: a volcanic field is placed from distance to a crater, so the craters
      // have to exist first. Its own stage rather than more code in tectonics, because `Stage.version` reaches
      // the RNG and retuning vent spacing must not move every mountain in the world.
      VolcanismStage(base, p.volcanism),
      BiomeStage(base, p.biome),
      GlacialStage(base, p.glacial),
      // After glacial, because a moraine dam only exists once the ice that left it has been extracted -
      // and *not* inside hydrology, because the whole point of these ponds is the water priority-flood
      // cannot find. See `hydro/PondStage.kt`.
      PondStage(base, p.pond),
      // The sediment half of the same idea: sub-kilometre shapes on the floodplain that the raster cannot
      // hold. Fed from the erosion stage's budget rather than replacing it - see `hydro/AlluviumStage.kt`.
      AlluviumStage(base, p.alluvium),
      // The kilometre summary of the chunk tier's own scatter, built from the same tuning object the
      // materialiser gets - so "how wooded is this cell" and "is there a tree at this position" are two
      // views of one function rather than two models of one thing.
      VegetationStage(base, p.vegetation),
      ResourceStage(base, p.resource),
      // Caves take the chunk tier's own rock tuning rather than a copy of it, so "where is the limestone" has
      // one answer for the stage that places a passage and the materialiser that cuts it.
      CaveStage(base, p.cave, p.strata),
      // Before history, and that ordering is the whole reason mana and corruption are two stages:
      // history has to react to where mana wells up, and corruption has to know which settlements
      // history left standing. See `mana/ManaStage.kt`.
      ManaStage(base, p.mana),
      HabitabilityStage(base, p.habitability),
      SettlementStage(base, p.settlement),
      HistoryStage(base, p.history),
      // After history, so the settlements it suppresses by are the ones somebody still lives in.
      CorruptionStage(base, p.corruption),
      // Last of the world tier in dependency terms: it reads the corruption, the settlements and what history
      // left standing of them.
      SpawnerStage(base, p.spawner),
      TownStage(base, p.town),
      EconomyStage(base, p.economy),
      // Last, and it has to be: the routes NPCs walk are read off the roads, bridges, gates and cave mouths
      // every stage above put down, so it can only run once all of them are final. Nothing reads it back.
      NavGraphStage(base, p.nav)
    )
  }

  fun pipeline(config: WorldConfig, params: WorldParams = WorldParams.DEFAULT) =
    WorldGenPipeline(stages(config, params), params.chunkTierVersion)

  /**
   * Runs the world tier and assembles the chunk samplers on top of it.
   *
   * The stage graph is built from [config] rather than being a constant, because climate's resolution
   * depends on how big the world is - see [climateResolutionFor].
   */
  fun build(
    config: WorldConfig,
    listener: StageListener = StageListener.NONE,
    params: WorldParams = WorldParams.DEFAULT
  ): GeneratedWorld {
    val world = pipeline(config, params).generateWorld(config, listener)
    return assemble(world, params)
  }

  /**
   * The chunk tier for an already-generated world. Separate so a cached world tier can be reused.
   *
   * Tuning arrives as [WorldParams] rather than as `WorldConfig` fields, deliberately, and the reasoning is
   * worth keeping because it is what the whole params-file design rests on. A `WorldConfig` field that decides
   * terrain has to join `shapeVersion`'s explicit list and then `PersistedWorld`, `WorldConfigMapping`,
   * `WorldGenSettings.FLAGS` and `WorldArgs` all need it - four files and a database column each. Params ride
   * `pipelineVersion` instead, which is *already* a persisted column, so the whole set costs nothing new: the
   * server stores the number that identifies the generator and compares it on every boot, and no individual
   * tunable needs a home in the schema.
   */
  fun assemble(world: World, params: WorldParams = WorldParams.DEFAULT): GeneratedWorld {
    val config = world.config
    val p = params.resolved
    val elevation = world.layers.require<FloatLayer>(LayerId.ELEVATION)
    val hardness = world.layers.require<FloatLayer>(LayerId.ROCK_HARDNESS)

    val analytic = WorldHeightField(
      elevation = elevation,
      hardness = hardness,
      seed = config.seed,
      seaLevel = config.seaLevel,
      params = p.detail
    )

    // Wrapped, not replaced: with droplets off this returns the analytic field's own value bit for bit, so the
    // default path is exactly what it was. See DropletHeightField for why the wrapper cannot introduce a seam.
    val base: BaseHeightField = if (p.droplets.enabled) {
      DropletHeightField(analytic, config.seed, p.droplets)
    } else {
      analytic
    }

    val columns = ChunkHeightSampler(config, base, world.features)

    val materializer = ChunkMaterializer(
      config = config,
      columns = columns,
      strata = Stratigraphy.of(world.layers, config, p.strata),
      surface = SurfaceSampler.of(world.layers, config),
      features = world.features,
      caveParams = p.cave,
      vegetationParams = p.vegetation,
      grades = p.resource.grades,
      // Read off the store rather than required: `assemble` is also called on a cached world tier and on the
      // partial pipelines the viewer opens, and "no corruption stage ran" is a legitimate world.
      corruption = world.layers[LayerId.CORRUPTION] as? FloatLayer,
      aetheriteCorruption = p.corruption.aetheriteCorruption,
      crystalParams = p.crystal
    )

    return GeneratedWorld(world, base, columns, materializer, p)
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
