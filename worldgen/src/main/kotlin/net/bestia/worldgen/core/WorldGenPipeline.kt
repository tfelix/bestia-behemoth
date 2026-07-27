package net.bestia.worldgen.core

/**
 * The result of running the world-scale stages: the immutable world tier.
 *
 * Raster layers, vector features and (later) the history event log are computed once and are
 * read-only forever, so they can be replicated to every node and need no coordination. All the
 * genuinely distributed work - region and chunk generation - reads this and writes nothing back.
 */
class World(
  val config: WorldConfig,
  val layers: LayerStore,
  val features: FeatureStore,
  /** Folds in every stage's version; part of every cache key and of the client version gate. */
  val pipelineVersion: Long
)

/** Called as stages start and finish, so a viewer or a build log can show progress. */
interface StageListener {
  fun onStageStart(stage: Stage, region: CellRegion) {}
  fun onStageFinish(stage: Stage, region: CellRegion, result: StageResult, millis: Long) {}

  companion object {
    val NONE = object : StageListener {}
  }
}

/**
 * The stage DAG: validation, topological ordering, version vectors, and execution of the
 * world-scale tier.
 *
 * The pipeline is constructed once from a stage list and is immutable. Ordering is deterministic -
 * ties in the topological sort are broken by stage name, never by hash-map iteration order - so two
 * nodes that build the same pipeline execute it in the same order and derive the same RNG streams.
 */
class WorldGenPipeline(stages: List<Stage>) {

  /** Topologically ordered: every stage appears after all of its dependencies. */
  val stages: List<Stage>

  private val byId: Map<StageId, Stage>

  /**
   * Cache key per stage: a hash of the stage's own version and every transitive upstream version.
   *
   * This is what makes a version bump surgical. Changing an erosion parameter invalidates erosion
   * and everything downstream of it, and leaves tectonics - the expensive part - alone.
   */
  private val versionVector: Map<StageId, Long>

  val pipelineVersion: Long

  init {
    require(stages.isNotEmpty()) { "A pipeline needs at least one stage" }

    val duplicates = stages.groupBy { it.id }.filterValues { it.size > 1 }.keys
    require(duplicates.isEmpty()) { "Duplicate stage ids: $duplicates" }

    byId = stages.associateBy { it.id }

    for (stage in stages) {
      for (dependency in stage.dependencies) {
        require(dependency in byId) {
          "Stage ${stage.id} depends on $dependency, which is not part of the pipeline"
        }
      }
    }

    this.stages = topologicallySort(stages, byId)

    val versions = LinkedHashMap<StageId, Long>()
    for (stage in this.stages) {
      // Dependencies are already resolved: the list is topologically ordered.
      var h = GenRng.hash(stage.id.hash, stage.version.toLong())
      for (dependency in stage.dependencies.sortedBy { it.name }) {
        h = GenRng.hash(h, versions.getValue(dependency))
      }
      versions[stage.id] = h
    }
    versionVector = versions

    var combined = 0L
    for (stage in this.stages) {
      combined = GenRng.hash(combined, versions.getValue(stage.id))
    }
    pipelineVersion = combined
  }

  fun stage(id: StageId): Stage = byId[id] ?: throw IllegalArgumentException("No stage $id")

  /** Cache key contribution of [id]: its version folded with every transitive upstream version. */
  fun versionOf(id: StageId): Long =
    versionVector[id] ?: throw IllegalArgumentException("No stage $id")

  /** Cache key for one chunk under the current pipeline. */
  fun chunkCacheKey(seed: Long, chunk: ChunkPos): Long =
    GenRng.hash(seed, pipelineVersion, chunk.key())

  fun stagesAt(scale: StageScale): List<Stage> = stages.filter { it.scale == scale }

  /**
   * Runs every [StageScale.WORLD] stage in dependency order and returns the immutable world tier.
   *
   * Region and chunk stages are deliberately not run here: they are on demand, sharded, and cached,
   * and they read this result. See the scale-tier table in worldgen-architecture.md.
   */
  fun generateWorld(config: WorldConfig, listener: StageListener = StageListener.NONE): World {
    val layerStore = LayerStore()
    val featureStore = FeatureStore()

    for (stage in stagesAt(StageScale.WORLD)) {
      val region = config.worldRegion.at(stage.resolution)
      val result = execute(stage, config, layerStore, featureStore, region, listener)

      layerStore.putAll(stage.id, result)
      if (result.features.isNotEmpty()) {
        featureStore.add(stage.id, result.features)
        featureStore.reindex()
      }
    }

    featureStore.freeze()

    return World(config, layerStore, featureStore, pipelineVersion)
  }

  /**
   * The context a stage would see if it ran against an already-generated world.
   *
   * For tooling and tests: re-deriving one stage's intermediate values without re-running the pipeline, with
   * the same dependency scoping the real run would have enforced. Deliberately not a way to *run* a stage
   * again - the layer store would refuse the duplicate output, which is the correct answer.
   */
  fun contextFor(stage: Stage, world: World): GenContext = GenContext(
    stage = stage,
    config = world.config,
    layers = world.layers.scopedFor(stage.id, transitiveDependenciesOf(stage.id)),
    features = world.features.scopedFor(stage.id, transitiveDependenciesOf(stage.id))
  )

  /**
   * Runs a single stage against the given stores, checking that it produced exactly what it
   * declared.
   *
   * Verifying declared outputs is not paranoia: an undeclared output is invisible to the dependency
   * graph, so a downstream stage that reads it would work by accident until the scheduler sharded
   * the two onto different nodes.
   */
  fun execute(
    stage: Stage,
    config: WorldConfig,
    layerStore: LayerStore,
    featureStore: FeatureStore,
    region: CellRegion,
    listener: StageListener = StageListener.NONE
  ): StageResult {
    val visible = transitiveDependenciesOf(stage.id)
    val ctx = GenContext(
      stage = stage,
      config = config,
      layers = layerStore.scopedFor(stage.id, visible),
      features = featureStore.scopedFor(stage.id, visible)
    )

    listener.onStageStart(stage, region)
    val startedAt = System.nanoTime()
    val result = stage.generate(ctx, region)
    val millis = (System.nanoTime() - startedAt) / 1_000_000

    verifyOutputs(stage, result)
    listener.onStageFinish(stage, region, result, millis)

    return result
  }

  /**
   * A stage may read its direct dependencies *and* everything they in turn depend on.
   *
   * Restricting to direct dependencies only would be stricter but would force every stage to
   * re-declare the whole upstream chain, which in practice makes people declare everything and the
   * check stops meaning anything.
   */
  fun transitiveDependenciesOf(id: StageId): Set<StageId> {
    val visible = LinkedHashSet<StageId>()
    val pending = ArrayDeque<StageId>()
    pending.addAll(byId.getValue(id).dependencies)

    while (pending.isNotEmpty()) {
      val next = pending.removeFirst()
      if (!visible.add(next)) continue
      pending.addAll(byId.getValue(next).dependencies)
    }

    return visible
  }

  private fun verifyOutputs(stage: Stage, result: StageResult) {
    val declaredLayers = stage.outputs.filterIsInstance<StageOutput.Raster>().map { it.layer }.toSet()
    val producedLayers = result.layers.map { it.id }.toSet()

    check(producedLayers == declaredLayers) {
      val missing = declaredLayers - producedLayers
      val extra = producedLayers - declaredLayers
      "Stage ${stage.id} declared layers $declaredLayers but produced $producedLayers" +
          (if (missing.isNotEmpty()) "; missing $missing" else "") +
          (if (extra.isNotEmpty()) "; undeclared $extra" else "")
    }

    val declaredKinds = stage.outputs.filterIsInstance<StageOutput.Vector>().map { it.kind }.toSet()
    val producedKinds = result.features.map { it.kind }.toSet()

    check(producedKinds.all { it in declaredKinds }) {
      "Stage ${stage.id} emitted undeclared feature kinds ${producedKinds - declaredKinds}"
    }
  }

  companion object {

    /**
     * Kahn's algorithm with the ready set kept sorted by stage name.
     *
     * The sort matters. An unordered ready set would let two nodes execute independent stages in
     * different orders, which is harmless for pure stages but makes any future ordering-sensitive
     * bug intermittent and node-specific - the worst kind to find.
     */
    private fun topologicallySort(stages: List<Stage>, byId: Map<StageId, Stage>): List<Stage> {
      val remaining = stages.associate { it.id to it.dependencies.toMutableSet() }.toMutableMap()
      val ordered = ArrayList<Stage>(stages.size)

      while (remaining.isNotEmpty()) {
        val ready = remaining.filterValues { it.isEmpty() }.keys.sortedBy { it.name }

        if (ready.isEmpty()) {
          throw IllegalArgumentException(
            "Cycle in the stage graph among ${remaining.keys.map { it.name }.sorted()}"
          )
        }

        for (id in ready) {
          ordered.add(byId.getValue(id))
          remaining.remove(id)
        }
        for (deps in remaining.values) {
          deps.removeAll(ready.toSet())
        }
      }

      return ordered
    }
  }
}
