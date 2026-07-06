package net.bestia.zone.ai.goal.consideration

import org.springframework.stereotype.Component

/**
 * Maps a raw normalised consideration input (expected in `[0,1]`) to a scored value in `[0,1]`.
 * Curves are referenced by [id] from the `curve:` key of a consideration in a YAML archetype. Add a
 * new curve simply by dropping in a new bean.
 */
interface ResponseCurve {
  val id: String
  fun apply(x: Double): Double
}

private fun clamp01(x: Double): Double = x.coerceIn(0.0, 1.0)

/** Passes the input through unchanged. */
@Component
class IdentityCurve : ResponseCurve {
  override val id = "identity"
  override fun apply(x: Double): Double = clamp01(x)
}

/** Inverts the input: high input -> low score (e.g. low HP -> high urge to flee). */
@Component
class InverseCurve : ResponseCurve {
  override val id = "inverse"
  override fun apply(x: Double): Double = clamp01(1.0 - x)
}

/** Linearly rising response; distinct name from identity so archetypes can express intent. */
@Component
class LinearRisingCurve : ResponseCurve {
  override val id = "linear_rising"
  override fun apply(x: Double): Double = clamp01(x)
}
