package net.bestia.worldgen.core

import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureIndex
import net.bestia.worldgen.vector.VectorFeature

/**
 * The world's vector tier: every river reach, trough, road and settlement footprint, in world space
 * at full precision, with a spatial index over them.
 *
 * Once the vector-producing stages have run this is immutable and read-only forever, which is why
 * it can be replicated to every node and needs no coordination. For a 4096 km world it is a few
 * hundred megabytes - a startup cost, never a per-chunk cost. Chunk generation fetches nothing over
 * the network for features; every node already has all of them.
 */
class FeatureStore {

  private val features = LinkedHashMap<FeatureId, VectorFeature>()
  private val producers = LinkedHashMap<FeatureId, StageId>()

  private var index: FeatureIndex = FeatureIndex.empty()
  private var frozen = false

  val size get() = features.size

  @Synchronized
  fun add(producer: StageId, produced: Collection<VectorFeature>) {
    check(!frozen) { "The feature store is frozen; $producer cannot add features to it" }

    for (feature in produced) {
      val existing = producers[feature.id]
      require(existing == null) {
        "FeatureId ${feature.id} was already emitted by $existing; ids must be globally unique"
      }
      features[feature.id] = feature
      producers[feature.id] = producer
    }
  }

  /** Rebuilds the spatial index. Call after each vector-producing stage. */
  @Synchronized
  fun reindex() {
    index = FeatureIndex.build(features.values)
  }

  /**
   * Rebuilds the index and closes the store for writing. After this the vector tier is immutable,
   * which is the property the whole distribution story depends on.
   */
  @Synchronized
  fun freeze() {
    reindex()
    frozen = true
  }

  @Synchronized
  fun producerOf(id: FeatureId): StageId? = producers[id]

  /** Unrestricted query. Chunk generation uses this; stages go through [scopedFor]. */
  @Synchronized
  fun query(area: Aabb): List<VectorFeature> = index.query(area)

  @Synchronized
  fun all(): List<VectorFeature> = features.values.toList()

  fun scopedFor(reader: StageId, allowed: Collection<StageId>) =
    ScopedFeatureStore(this, reader, allowed.toSet())
}

/**
 * The feature view a stage gets: only features emitted by its declared dependencies.
 *
 * Filtering happens after the index query rather than through per-stage indices. With a handful of
 * candidates per query that is cheaper than maintaining one index per dependency set, and it keeps
 * the ordering guarantee - results stay in `(priority, id)` order because filtering preserves order.
 */
class ScopedFeatureStore internal constructor(
  private val delegate: FeatureStore,
  private val reader: StageId,
  private val allowed: Set<StageId>
) {

  fun query(area: Aabb): List<VectorFeature> =
    delegate.query(area).filter { delegate.producerOf(it.id) in allowed }

  /**
   * Like [query] but fails loudly when a feature from an undeclared stage would have matched,
   * instead of silently returning less than the caller expected.
   *
   * Use it in stages; a silent filter is exactly the kind of thing that makes a stage produce
   * different output depending on which other stages happened to have run.
   */
  fun queryStrict(area: Aabb): List<VectorFeature> {
    val hits = delegate.query(area)
    val undeclared = hits.mapNotNull { delegate.producerOf(it.id) }.toSet() - allowed
    check(undeclared.isEmpty()) {
      "Stage $reader queried an area containing features from undeclared stages $undeclared"
    }
    return hits
  }
}
