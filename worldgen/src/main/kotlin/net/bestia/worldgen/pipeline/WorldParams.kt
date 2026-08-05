package net.bestia.worldgen.pipeline

import net.bestia.worldgen.bio.BiomeParams
import net.bestia.worldgen.civ.HabitabilityParams
import net.bestia.worldgen.civ.NavParams
import net.bestia.worldgen.civ.SettlementParams
import net.bestia.worldgen.civ.TownParams
import net.bestia.worldgen.climate.ClimateParams
import net.bestia.worldgen.climate.WeatherParams
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.geo.DetailParams
import net.bestia.worldgen.geo.DropletParams
import net.bestia.worldgen.geo.ErosionParams
import net.bestia.worldgen.geo.GlacialParams
import net.bestia.worldgen.geo.TectonicsParams
import net.bestia.worldgen.geo.VolcanismParams
import net.bestia.worldgen.history.HistoryParams
import net.bestia.worldgen.hydro.HydrologyParams
import net.bestia.worldgen.hydro.AlluviumParams
import net.bestia.worldgen.hydro.PondParams
import net.bestia.worldgen.karst.CaveParams
import net.bestia.worldgen.poi.PoiParams
import net.bestia.worldgen.pop.EconomyParams
import net.bestia.worldgen.mana.CorruptionParams
import net.bestia.worldgen.mana.ManaParams
import net.bestia.worldgen.resource.ResourceParams
import net.bestia.worldgen.spawn.SpawnerParams
import net.bestia.worldgen.spawn.VegetationStandParams
import net.bestia.worldgen.voxel.ChunkMaterializer
import net.bestia.worldgen.voxel.AetheriteParams
import net.bestia.worldgen.voxel.CrystalParams
import net.bestia.worldgen.voxel.StrataParams
import net.bestia.worldgen.voxel.VegetationParams

/**
 * Every tunable in the generator, in one object.
 *
 * ### Why this exists at all
 *
 * Each stage has taken a params object since it was written, and until now **nothing ever passed one**:
 * [StandardWorld.stages] constructed all twelve with their resolution and let every params argument default. So
 * the two hundred-odd numbers in them were reachable in principle and unreachable in practice, and three of the
 * params classes - `DetailParams`, `StrataParams` and the street tuning - were not reachable even in principle.
 * A tunable nothing can set is not a tunable.
 *
 * Threading them through one object rather than twelve arguments is what makes a params *file* a change to one
 * place. It is also what lets the whole set be fingerprinted: see [version] and [chunkTierVersion].
 *
 * ### `pipeline/` rather than `core/`
 *
 * This has to name every stage's params type, and `core` may not import a stage package - it is above them in
 * the layering. `pipeline` already imports all twelve stages, and `zone-server` already imports
 * `pipeline.StandardWorld`, so nothing new is exposed by putting it here.
 *
 * ### The nesting is a hierarchy, not a duplication
 *
 * Four params objects hold another: erosion needs tectonics' ocean-margin figures, settlement needs the
 * habitability terms, town needs the settlement stage's grading limits and the chunk tier's detail noise. Those
 * are the same numbers read by two stages, and while every one of them defaulted they agreed for free. The
 * moment a file sets one side they diverge silently - a shallower erosion margin than tectonics carved,
 * buildings floating over ground graded to a different profile - so [resolved] forwards them from the one place
 * that owns each, and the nested fields are deliberately **not** settable from a params file.
 */
data class WorldParams(
  val tectonics: TectonicsParams = TectonicsParams(),
  val climate: ClimateParams = ClimateParams(),
  val erosion: ErosionParams = ErosionParams(),
  val glacial: GlacialParams = GlacialParams(),
  val hydrology: HydrologyParams = HydrologyParams(),

  /**
   * The vector-tier ponds, which depend on the glacial stage rather than the hydrological one.
   *
   * Beside [hydrology] because it is water, after [glacial] in the pipeline because it reads moraines.
   */
  val pond: PondParams = PondParams(),

  /** The sediment lobes rivers build: fans where they leave confinement, deltas where they meet the sea. */
  val alluvium: AlluviumParams = AlluviumParams(),

  /**
   * Where the craters are and how volcanic each province is.
   *
   * Before [biome] because the volcanic biomes are placed from distance to a vent, and its own params object
   * rather than a corner of [tectonics] because retuning vent rarity must not reseed the plates.
   */
  val volcanism: VolcanismParams = VolcanismParams(),
  val biome: BiomeParams = BiomeParams(),

  /**
   * The one params object read by a stage *and* by the chunk tier as the same numbers.
   *
   * `VegetationStage` averages the canopy from it and `ChunkMaterializer` plants trees from it, so it is
   * folded into [version] with the stages and into [chunkTierVersion] with the tier. Twice is right: either
   * alone would be a number that can move without one of its two readers noticing.
   */
  val vegetation: VegetationParams = VegetationParams(),
  val resource: ResourceParams = ResourceParams(),
  val cave: CaveParams = CaveParams(),

  /** Where the world's mana is. Read by history, so it runs before it - see [ManaStage]. */
  val mana: ManaParams = ManaParams(),
  val habitability: HabitabilityParams = HabitabilityParams(),
  val settlement: SettlementParams = SettlementParams(),
  val history: HistoryParams = HistoryParams(),

  /**
   * What the mana did to the land. After [history] in this list because it is after it in the pipeline: it
   * suppresses by the settlements history left *standing*.
   */
  val corruption: CorruptionParams = CorruptionParams(),

  /** Where the wild things are. After [corruption] in this list because it reads it. */
  val spawner: SpawnerParams = SpawnerParams(),

  /**
   * The patches of wood a runtime looks after. Beside [spawner] because it is the same kind of thing.
   *
   * Folded into [version] only. Nothing in the chunk tier reads it - a stand advertises a capacity computed
   * from [vegetation], which *is* folded twice, so the two tiers stay in step through that rather than
   * through this.
   */
  val vegetationStand: VegetationStandParams = VegetationStandParams(),
  val town: TownParams = TownParams(),
  val economy: EconomyParams = EconomyParams(),

  /**
   * The hand-authored landmarks, and how far each keeps from everything already built.
   *
   * Folded into [version] and **not** [chunkTierVersion], for the reason [nav] is not: where a POI stands is a
   * world-tier product like any marker, and the chunk tier only reads the marker back. What decides *which*
   * landmarks a world holds is not in here at all - it is the `PoiKind` catalogue, folded into
   * `PoiStage.paramsVersion` directly, because a table in an enum is a tunable in everything but its storage.
   */
  val poi: PoiParams = PoiParams(),

  /**
   * The macro navigation graph NPCs plan journeys over. Last, because it reads everything before it.
   *
   * Folded into [version] and not [chunkTierVersion]: the graph is a world-tier product like any stage
   * output, and no chunk's voxels depend on it.
   */
  val nav: NavParams = NavParams(),

  /**
   * The chunk tier: the base heightfield's detail noise, the rock column, the droplet field.
   *
   * Not a stage between them, and until this existed not reachable from here at all - `assemble` built
   * `WorldHeightField` and `Stratigraphy` with their defaults and there was no argument to pass. They decide
   * terrain, so they belong in the same object as everything else that does.
   */
  val detail: DetailParams = DetailParams(),
  val strata: StrataParams = StrataParams(),
  val droplets: DropletParams = DropletParams(),

  /**
   * The mana crystal scatter. Chunk tier only, unlike [vegetation].
   *
   * Folded into [chunkTierVersion] and **not** into [version], because no stage reads it - there is no
   * kilometre-scale summary of crystals the way `CANOPY_COVER` is one of trees, and nothing upstream needs
   * one. If a stage ever does, this has to be folded twice, for the reason [vegetation] is.
   */
  val crystal: CrystalParams = CrystalParams(),

  /**
   * The aetherite outcrop scatter. Chunk tier only, for [crystal]'s reason and with one addition of its own.
   *
   * It reads `FeatureKind.ORE_DEPOSIT` markers, which the resource *stage* produces - but it reads them at
   * materialisation rather than contributing to them, so retuning this cannot change a deposit and does not
   * belong in [version]. What it can change is which chunks hold shards, which is exactly what
   * [chunkTierVersion] keys the chunk cache on.
   */
  val aetherite: AetheriteParams = AetheriteParams(),

  /**
   * The weather model.
   *
   * **Folded into neither [version] nor [chunkTierVersion], and that is the point rather than an oversight.**
   * Weather is not a stage and has no cached artefact keyed on it: it is `f(seed, region, t)` evaluated on
   * demand, where a stage is `f(seed, region, upstream)` with no `t` in the signature at all. Folding this in
   * would make retuning how often it rains refuse every existing world at the boot gate and invalidate every
   * cached chunk in it, for a number that cannot move a voxel.
   *
   * It lives here anyway so a params file can reach it, and so `ParamsVersionTest`'s completeness oracle covers
   * it - its digest is still pinned there, as a stability check rather than as a cache key.
   */
  val weather: WeatherParams = WeatherParams()
) {

  /**
   * This object with every derived copy forwarded from the field that owns it.
   *
   * Computed once, lazily, and used by everything below - so there is no path that reads an unresolved nested
   * value, and no caller that has to remember to resolve first.
   */
  val resolved: WorldParams by lazy {
    val settlementResolved = settlement.copy(habitability = habitability)
    copy(
      // Erosion reapplies the ocean margin after the timesteps lift it, so it has to use the depth and wobble
      // tectonics carved. Two copies of a number that must agree is one copy too many.
      erosion = erosion.copy(
        oceanBorderDepth = tectonics.oceanBorderDepth,
        oceanBorderWobble = tectonics.oceanBorderWobble
      ),
      // Settlement scores sites against the habitability terms; scoring against different weights than the
      // layer was built with would place towns by one rule and rate them by another.
      settlement = settlementResolved,
      // The pond stage walks outward from a valley axis until the ground rises above the water, so it has to
      // walk the surface a chunk will build. Its own detail noise would put every shoreline somewhere else.
      pond = pond.copy(detail = detail),
      // The town stage predicts the grading feature's cut and fill in order to pick a building's floor, and
      // samples the same detail noise the chunks will. Both are the settlement stage's and the chunk tier's
      // numbers respectively, not its own.
      town = town.copy(grading = settlementResolved, detail = detail),
      // The navigation graph decides which of its hops are river fords, and "there is a river in this cell"
      // is the habitability stage's threshold, not a second opinion. See `NavParams.habitability`.
      nav = nav.copy(habitability = habitability)
    )
  }

  /**
   * Fingerprint of the world-tier stages' tunables, for `Stage.paramsVersion` to be checked against.
   *
   * Computed from [resolved] rather than from the declared fields, so a forwarded value is always visible in the
   * hash. Not used by the pipeline directly - each stage folds its own params - but it is what a tool prints so
   * that two runs which disagree can be told apart by more than their output.
   */
  val version: Long by lazy {
    val r = resolved
    GenRng.hash(
      r.tectonics.digest().value,
      r.climate.digest().value,
      r.erosion.digest().value,
      r.glacial.digest().value,
      r.hydrology.digest().value,
      r.pond.digest().value,
      r.alluvium.digest().value,
      r.volcanism.digest().value,
      r.biome.digest().value,
      r.vegetation.digest().value,
      r.resource.digest().value,
      r.cave.digest().value,
      r.mana.digest().value,
      r.habitability.digest().value,
      r.settlement.digest().value,
      r.history.digest().value,
      r.corruption.digest().value,
      r.spawner.digest().value,
      r.vegetationStand.digest().value,
      r.town.digest().value,
      r.economy.digest().value,
      r.poi.digest().value,
      r.nav.digest().value
    )
  }

  /**
   * Fingerprint of everything after the stage graph, for `WorldGenPipeline`'s own constructor.
   *
   * This is the half that was invisible: the chunk cache key is `(seed, pipelineVersion, chunk)`, and until this
   * was folded in, moving `DetailParams.amplitude` left every cached chunk looking valid while the ground under
   * it had changed.
   *
   * [ChunkMaterializer.VERSION] is folded in beside the digests because *code* in the chunk tier had the same
   * hole that its *values* did. Every stage carries a hand-written `version` for exactly this, and the tier
   * below them - which decides the blocks a player stands on - carried none, so teaching it to subtract changed
   * every mine head in every world and no number moved.
   */
  val chunkTierVersion: Long by lazy {
    val r = resolved
    GenRng.hash(
      ChunkMaterializer.VERSION.toLong(),
      r.detail.digest().value,
      r.strata.digest().value,
      r.droplets.digest().value,
      r.vegetation.digest().value,
      // Only the grade mix, not the whole of `resource`: the rest of that class decides where deposits go,
      // which is a world-tier question already folded into `version`. The mix is what `OreVeins` reads to
      // decide which of the three ore blocks a voxel is, so it belongs on this side too.
      r.resource.grades.digest().value,
      r.crystal.digest().value,
      r.aetherite.digest().value
    )
  }

  companion object {

    /** The defaults, which is what every caller that has no params file wants. */
    val DEFAULT = WorldParams()

    /**
     * The prefixes whose loaders are not written yet.
     *
     * Declared so the queue is visible in code rather than inferable from which `overriddenBy` methods happen to
     * exist, and so a file that sets one of these gets "cannot be set from a file yet" instead of a suggestion
     * that it meant something else entirely. The idiom is `WorldGenSettings.IGNORED`.
     *
     * The first tranche is what a designer reaches for first and what the sweep already measures - land
     * fraction and lake counts come straight out of tectonics, erosion and its basins - so a tuning run is
     * verifiable on the day the format lands rather than after all seventeen classes are wired.
     */
    val NOT_YET_LOADABLE = setOf(
      "glacial", "hydrology", "pond", "alluvium", "biome", "vegetation", "habitability", "settlement",
      "town", "economy", "detail", "strata", "crystal", "aetherite", "spawner", "vegetationStand"
    )

    /**
     * Applies a params file to [base], then reports anything in it that nobody read.
     *
     * The check lives here rather than with the caller because it is only correct once *every* loader has run:
     * "unknown" is defined as "no reader asked for it", so asking before the last reader has run would report a
     * key that is about to be read. One function means there is no order for a caller to get wrong.
     */
    fun load(text: ParamsText, base: WorldParams = DEFAULT): WorldParams {
      val loaded = base.copy(
        tectonics = base.tectonics.overriddenBy(text.scope("tectonics")),
        climate = base.climate.overriddenBy(text.scope("climate")),
        erosion = base.erosion.overriddenBy(text.scope("erosion")),
        volcanism = base.volcanism.overriddenBy(text.scope("volcanism")),
        resource = base.resource.overriddenBy(text.scope("resource")),
        cave = base.cave.overriddenBy(text.scope("cave")),
        mana = base.mana.overriddenBy(text.scope("mana")),
        // Off `NOT_YET_LOADABLE` because the Orders needed it: their influence over a world's history is the
        // one number in `HistoryParams` a server sets per world rather than a designer setting once, and while
        // this class was unloadable there was no path for it at all. The other fifty fields came along free.
        history = base.history.overriddenBy(text.scope("history")),
        corruption = base.corruption.overriddenBy(text.scope("corruption")),
        weather = base.weather.overriddenBy(text.scope("weather")),
        droplets = base.droplets.overriddenBy(text.scope("droplets")),
        // Loadable from the day it lands, unlike the sixteen prefixes above: every field in it is a *clearance*,
        // and whether a landmark is far enough from a road is exactly the sort of thing found by looking at one
        // and then moving a number.
        poi = base.poi.overriddenBy(text.scope("poi")),
        nav = base.nav.overriddenBy(text.scope("nav"))
      )
      text.checkAllConsumed(NOT_YET_LOADABLE)
      return loaded
    }
  }
}
