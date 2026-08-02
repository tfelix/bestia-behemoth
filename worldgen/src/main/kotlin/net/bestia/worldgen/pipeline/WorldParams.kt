package net.bestia.worldgen.pipeline

import net.bestia.worldgen.bio.BiomeParams
import net.bestia.worldgen.civ.HabitabilityParams
import net.bestia.worldgen.civ.SettlementParams
import net.bestia.worldgen.civ.TownParams
import net.bestia.worldgen.climate.ClimateParams
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.geo.DetailParams
import net.bestia.worldgen.geo.DropletParams
import net.bestia.worldgen.geo.ErosionParams
import net.bestia.worldgen.geo.GlacialParams
import net.bestia.worldgen.geo.TectonicsParams
import net.bestia.worldgen.history.HistoryParams
import net.bestia.worldgen.hydro.HydrologyParams
import net.bestia.worldgen.karst.CaveParams
import net.bestia.worldgen.pop.EconomyParams
import net.bestia.worldgen.resource.ResourceParams
import net.bestia.worldgen.voxel.ChunkMaterializer
import net.bestia.worldgen.voxel.StrataParams

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
  val biome: BiomeParams = BiomeParams(),
  val resource: ResourceParams = ResourceParams(),
  val cave: CaveParams = CaveParams(),
  val habitability: HabitabilityParams = HabitabilityParams(),
  val settlement: SettlementParams = SettlementParams(),
  val history: HistoryParams = HistoryParams(),
  val town: TownParams = TownParams(),
  val economy: EconomyParams = EconomyParams(),

  /**
   * The chunk tier: the base heightfield's detail noise, the rock column, the droplet field.
   *
   * Not a stage between them, and until this existed not reachable from here at all - `assemble` built
   * `WorldHeightField` and `Stratigraphy` with their defaults and there was no argument to pass. They decide
   * terrain, so they belong in the same object as everything else that does.
   */
  val detail: DetailParams = DetailParams(),
  val strata: StrataParams = StrataParams(),
  val droplets: DropletParams = DropletParams()
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
      // The town stage predicts the grading feature's cut and fill in order to pick a building's floor, and
      // samples the same detail noise the chunks will. Both are the settlement stage's and the chunk tier's
      // numbers respectively, not its own.
      town = town.copy(grading = settlementResolved, detail = detail)
    )
  }

  /**
   * Fingerprint of the twelve world-tier stages' tunables, for `Stage.paramsVersion` to be checked against.
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
      r.biome.digest().value,
      r.resource.digest().value,
      r.cave.digest().value,
      r.habitability.digest().value,
      r.settlement.digest().value,
      r.history.digest().value,
      r.town.digest().value,
      r.economy.digest().value
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
      r.droplets.digest().value
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
      "glacial", "hydrology", "biome", "resource", "habitability", "settlement", "history", "town", "economy",
      "detail", "strata"
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
        cave = base.cave.overriddenBy(text.scope("cave")),
        droplets = base.droplets.overriddenBy(text.scope("droplets"))
      )
      text.checkAllConsumed(NOT_YET_LOADABLE)
      return loaded
    }
  }
}
