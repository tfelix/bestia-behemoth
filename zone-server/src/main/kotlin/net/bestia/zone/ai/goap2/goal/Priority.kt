package net.bestia.zone.ai.goap2.goal

import net.bestia.zone.ai.goap2.state.WorldState

/**
 * How multiple [Consideration]s combine into one `0.0..1.0` score, mirroring the combine modes of
 * the old utility-AI archetype format (product/min/max), plus [MEAN] as the simple default.
 */
enum class Combine {
  MEAN,
  PRODUCT,
  MIN,
  MAX;

  fun fold(values: List<Double>): Double = when (this) {
    MEAN -> if (values.isEmpty()) 1.0 else values.average()
    PRODUCT -> values.fold(1.0) { acc, v -> acc * v }
    MIN -> values.minOrNull() ?: 1.0
    MAX -> values.maxOrNull() ?: 1.0
  }
}

/** One weighted [Curve] feeding into a [Priority]. */
data class Consideration(val curve: Curve, val weight: Double = 1.0) {
  fun evaluate(state: WorldState): Double = (weight * curve.evaluate(state)).coerceIn(0.0, 1.0)
}

/**
 * A goal's dynamic priority: [base] scaled by its [considerations] folded together via [combine].
 * Replaces the old bare `basePriority` + `curves` pair on [Goal] with one small declarative value,
 * built through the [priority] DSL rather than assembled by hand.
 */
class Priority(
  val base: Float,
  val combine: Combine = Combine.MEAN,
  val considerations: List<Consideration> = emptyList(),
) {
  fun evaluate(state: WorldState): Float {
    if (considerations.isEmpty()) return base
    return (base * combine.fold(considerations.map { it.evaluate(state) })).toFloat()
  }
}

/** Builder behind the [priority] DSL. */
class PriorityBuilder(private val base: Float, private val combine: Combine) {
  private val considerations = mutableListOf<Consideration>()

  fun consider(curve: Curve, weight: Double = 1.0) {
    considerations += Consideration(curve, weight)
  }

  fun build(): Priority = Priority(base, combine, considerations.toList())
}

/**
 * `priority(80f) { consider(HUNGER.inverseLinear()) }` — declares a goal's priority formula inline
 * instead of assembling a `curves` list by hand.
 */
fun priority(base: Float, combine: Combine = Combine.MEAN, block: PriorityBuilder.() -> Unit = {}): Priority =
  PriorityBuilder(base, combine).apply(block).build()
