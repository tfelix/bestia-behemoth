package net.bestia.worldgen.core

import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.VectorFeature

/**
 * Identity of a pipeline stage. Also the salt that separates one stage's RNG streams from another's.
 */
data class StageId(val name: String) {

  /** Precomputed so RNG derivation does not re-hash the name on every call. */
  val hash: Long = GenRng.hashString(name)

  override fun toString() = name
}

/**
 * Where a stage runs.
 *
 * [WORLD] stages need global coherence - plate boundaries, priority-flood, the history simulation -
 * and run once on a single node at world creation. [REGION] and [CHUNK] stages are sharded across
 * the cluster. Vector-producing stages are almost always [WORLD], which conveniently means they
 * never need distributing at all.
 */
enum class StageScale { WORLD, REGION, CHUNK }

/** What a stage promises to produce. Declared so the scheduler can wire dependencies by output. */
sealed interface StageOutput {
  data class Raster(val layer: LayerId) : StageOutput
  data class Vector(val kind: FeatureKind) : StageOutput

  /**
   * The world's history log. At most one stage in a pipeline may declare it.
   *
   * Not parameterised by anything, unlike the other two, because there is exactly one chronicle per
   * world - the log is a single object, not a keyed collection, and pretending otherwise would invite a
   * second stage to write a second history of the same world.
   */
  data object History : StageOutput
}

/** Everything one stage invocation produced. */
class StageResult(
  val layers: List<LayerData> = emptyList(),
  val features: List<VectorFeature> = emptyList(),
  /** Non-null exactly when the stage declared [StageOutput.History]; the pipeline checks that. */
  val chronicle: Chronicle? = null
) {
  companion object {
    val EMPTY = StageResult()

    fun of(vararg layers: LayerData) = StageResult(layers.toList())
  }
}

/**
 * One step of the world generation pipeline: a pure function of `(seed, region, upstream layers)`.
 *
 * Purity is the whole architecture, not a stylistic preference. It is what lets any node compute
 * any chunk as long as it can fetch the dependencies, what makes caching by version vector correct,
 * and what would eventually let the client regenerate the base terrain itself.
 *
 * A stage must never read anything it did not declare in [dependencies] - the layer and feature
 * stores handed to it through [GenContext] enforce that rather than trusting it.
 */
interface Stage {

  val id: StageId

  /**
   * Bump on any change to what this stage computes. Cache keys fold in this version and every
   * upstream version, so bumping erosion invalidates erosion and everything downstream while
   * leaving tectonics alone.
   */
  val version: Int

  /** The stages this one is allowed to read. Reading anything else throws. */
  val dependencies: List<StageId>

  val scale: StageScale

  /** Independent of [scale]: climate can run at 4 km while the heightfield runs at 1 km. */
  val resolution: Resolution

  /** Neighbour cells this stage needs beyond its region, for raster stages that use a kernel. */
  val halo: Int get() = 0

  val outputs: List<StageOutput>

  fun generate(ctx: GenContext, region: CellRegion): StageResult
}
