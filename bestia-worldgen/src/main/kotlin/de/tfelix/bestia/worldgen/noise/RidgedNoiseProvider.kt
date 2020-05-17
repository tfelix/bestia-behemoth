package de.tfelix.bestia.worldgen.noise

import de.tfelix.bestia.worldgen.map.Point
import kotlin.math.abs

/**
 * Provides noise with a more riged edge. Can be used e.g. for mountains.
 */
class RidgedNoiseProvider(
    /**
     * Seed value for the simplex noise implementation.
     */
    seed: Long,
    /**
     * Constant scale factor used to multiply the incoming
     * coordinates with.
     */
    private val scale: Double = 1.0
) : NoiseProvider {

  private val simplexNoise: OpenSimplexNoise = OpenSimplexNoise(seed)

  /**
   * We want outputs between 0 and 1. Thus we need to re-norm the simplex
   * output.
   */
  override fun getRandom(coordinate: Point): Double {
    val x = coordinate.x * scale
    val y = coordinate.y * scale

    return 2 * (0.5 - abs(0.5 - simplexNoise.eval(x, y)))
  }
}
