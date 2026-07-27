package net.bestia.worldgen.vector

import kotlin.math.max
import kotlin.math.min

/**
 * Applies a set of vector features to a base heightfield, one column at a time.
 *
 * Construct one per chunk (or per worker thread) from the features the spatial index returned for
 * that chunk's expanded bounds. It is **not** thread-safe - it carries scratch state deliberately,
 * so that the features themselves can stay immutable and shared across every thread in the process.
 *
 * Features are applied in `(priority, id)` order and each one sees the height accumulated by every
 * feature below it. A road therefore follows the valley floor a river already cut, rather than
 * hovering over the terrain the river used to have.
 */
class FeatureEvaluator(features: List<VectorFeature>) : HeightModSink {

  /**
   * Kept in the order the index returned, which is already `(priority, id)`.
   *
   * Geometry-only features are dropped here rather than at the query, because downstream *stages*
   * legitimately want them - it is only terrain generation that has nothing to do with them.
   */
  private val features: List<VectorFeature> = features
    .filter { it.affectsHeight }
    .sortedWith(compareBy({ it.priority }, { it.id.value }))

  private val scratch = DoubleArray(this.features.maxOfOrNull { it.scratchSize } ?: 0)

  private var height = 0.0

  val featureCount get() = features.size

  val isEmpty get() = features.isEmpty()

  /**
   * The terrain height at world position ([x], [y]) after every feature has been stamped onto
   * [baseHeight].
   */
  fun heightAt(x: Double, y: Double, baseHeight: Double): Double {
    height = baseHeight

    for (feature in features) {
      feature.evaluateColumn(x, y, height, scratch, this)
    }

    return height
  }

  override fun add(
    featureId: FeatureId,
    priority: Int,
    blend: BlendMode,
    value: Double,
    weight: Double
  ) {
    if (weight <= 0.0) return

    val w = min(1.0, weight)
    val target = when (blend) {
      BlendMode.MIN -> min(height, value)
      BlendMode.MAX -> max(height, value)
      BlendMode.REPLACE -> value
      BlendMode.ADD -> height + value
    }

    height += (target - height) * w
  }
}
