package de.tfelix.bestia.worldgen.noise

import de.tfelix.bestia.worldgen.map.Point
import de.tfelix.bestia.worldgen.noise.NoiseProvider
import de.tfelix.bestia.worldgen.noise.OpenSimplexNoise

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
  override fun getRandom(coordinate: Point): Double {
    val x = coordinate.x * scale
    val y = coordinate.y * scale
    return (simplexNoise.eval(x, y) + 1.0) / 2
  }
}
