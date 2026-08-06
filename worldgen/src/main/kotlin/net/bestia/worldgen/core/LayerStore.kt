package net.bestia.worldgen.core

/**
 * Holds the raster layers the pipeline has produced so far, and remembers which stage produced each.
 *
 * The producer bookkeeping is not diagnostics: it is what lets [scopedFor] refuse a read of an
 * undeclared dependency. A stage that quietly reads a layer it did not declare still produces
 * correct-looking output today and breaks the moment the scheduler reorders or shards the pipeline,
 * which is the worst possible time to find out.
 */
class LayerStore {

  private val layers = LinkedHashMap<LayerId, LayerData>()
  private val producers = LinkedHashMap<LayerId, StageId>()

  @Synchronized
  fun put(producer: StageId, layer: LayerData) {
    val existing = producers[layer.id]
    require(existing == null || existing == producer) {
      "Layer ${layer.id} is already produced by $existing; $producer may not overwrite it"
    }
    layers[layer.id] = layer
    producers[layer.id] = producer
  }

  @Synchronized
  fun putAll(producer: StageId, result: StageResult) {
    for (layer in result.layers) {
      put(producer, layer)
    }
  }

  @Synchronized
  fun producerOf(id: LayerId): StageId? = producers[id]

  @Synchronized
  fun contains(id: LayerId) = layers.containsKey(id)

  @Synchronized
  fun ids(): List<LayerId> = layers.keys.toList()

  /**
   * Unrestricted read. Tooling - the offline viewer, the regression harness - uses this; stages go
   * through [scopedFor], which is where the dependency contract is enforced.
   */
  @Synchronized
  operator fun get(id: LayerId): LayerData? = layers[id]

  /**
   * A layer that must be present, of the type the caller expects.
   *
   * For tooling and for assembling the chunk tier, where a missing layer means the pipeline was built
   * wrong rather than that a stage read something it should not have. Stages must not use this - they go
   * through [scopedFor], which is where the dependency contract is enforced.
   */
  inline fun <reified T : LayerData> require(id: LayerId): T {
    val layer = this[id] ?: throw IllegalStateException(
      "No layer $id in this world; have ${ids().map { it.name }.sorted()}"
    )
    return layer as? T ?: throw IllegalStateException(
      "Layer $id is a ${layer::class.simpleName}, not a ${T::class.simpleName}"
    )
  }

  /**
   * A read-only view restricted to the layers produced by [allowed].
   *
   * [reader] is only used to make the failure message point at the stage with the missing
   * declaration rather than at the store.
   */
  fun scopedFor(reader: StageId, allowed: Collection<StageId>) =
    ScopedLayerStore(this, reader, allowed.toSet())
}

/**
 * The layer view a stage actually gets. Reading outside the declared dependency set throws, so an
 * undeclared read is a loud failure in the first test run rather than a subtle nondeterminism bug
 * once the pipeline is distributed.
 */
class ScopedLayerStore internal constructor(
  private val delegate: LayerStore,
  private val reader: StageId,
  private val allowed: Set<StageId>
) {

  fun float(id: LayerId): FloatLayer = get(id) as? FloatLayer
    ?: throw IllegalArgumentException("Layer $id is not a FloatLayer")

  fun int(id: LayerId): IntLayer = get(id) as? IntLayer
    ?: throw IllegalArgumentException("Layer $id is not an IntLayer")

  fun get(id: LayerId): LayerData {
    val producer = delegate.producerOf(id)
      ?: throw IllegalStateException("Stage $reader read layer $id, which no stage has produced")

    if (producer !in allowed) {
      throw IllegalStateException(
        "Stage $reader read layer $id produced by $producer, which it did not declare as a " +
            "dependency. Add $producer to its dependencies."
      )
    }

    return delegate[id]!!
  }

  fun contains(id: LayerId): Boolean {
    val producer = delegate.producerOf(id) ?: return false
    return producer in allowed
  }
}
