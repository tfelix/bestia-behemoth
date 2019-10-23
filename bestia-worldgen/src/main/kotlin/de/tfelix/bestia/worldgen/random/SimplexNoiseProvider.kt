package de.tfelix.bestia.worldgen.random

import de.tfelix.bestia.worldgen.map.Map2DDiscreteCoordinate

/**
 * Provides simplex noise type generation. This simply wraps the
 * [OpenSimplexNoise] implementation.
 *
 * @author Thomas Felix
 */
class SimplexNoiseProvider(
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
  override fun getRandom(coordinate: Map2DDiscreteCoordinate): Double {
    val x = coordinate.x * scale
    val y = coordinate.y * scale
    return (simplexNoise.eval(x, y) + 1.0) / 2
  }
}
