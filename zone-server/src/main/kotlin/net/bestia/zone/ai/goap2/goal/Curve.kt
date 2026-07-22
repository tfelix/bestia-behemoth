package net.bestia.zone.ai.goap2.goal

import net.bestia.zone.ai.goap2.state.StateKey
import net.bestia.zone.ai.goap2.state.WorldState

/**
 * A utility "consideration": maps some aspect of the world state to a
 * normalised weight in `0.0..1.0`. A goal's dynamic priority is its base
 * priority scaled by its curves, so the agent pursues whatever is most pressing
 * right now.
 *
 * Returning a [Double] in a fixed range (rather than the old raw `Int`) is what
 * fixes the integer-division collapse in the original blueprint.
 */
fun interface Curve {
  /** @return a weight clamped to `0.0..1.0`. */
  fun evaluate(state: WorldState): Double
}

private fun Double.clamp01(): Double = coerceIn(0.0, 1.0)

/**
 * Reads an Int state value assumed to live in `0..100` and normalises it to
 * `0.0..1.0`. Higher stored value → higher weight.
 */
class LinearCurve(
  private val key: StateKey<Int>,
  private val maxValue: Int = 100,
) : Curve {
  override fun evaluate(state: WorldState): Double =
    ((state.get(key) ?: 0).toDouble() / maxValue).clamp01()
}

/**
 * The inverse of [LinearCurve]: higher stored value → *lower* weight. Useful for
 * "the emptier my stomach, the more I want to eat" style considerations, where
 * the stored value is satiation but the urgency grows as it falls.
 */
class InverseLinearCurve(
  private val key: StateKey<Int>,
  private val maxValue: Int = 100,
) : Curve {
  override fun evaluate(state: WorldState): Double =
    (1.0 - (state.get(key) ?: 0).toDouble() / maxValue).clamp01()
}

/** `HUNGER.linear()` — reads naturally inside a [priority] block. */
fun StateKey<Int>.linear(maxValue: Int = 100): Curve = LinearCurve(this, maxValue)

/** `HUNGER.inverseLinear()` — the emptier the stat, the more urgent the goal. */
fun StateKey<Int>.inverseLinear(maxValue: Int = 100): Curve = InverseLinearCurve(this, maxValue)
