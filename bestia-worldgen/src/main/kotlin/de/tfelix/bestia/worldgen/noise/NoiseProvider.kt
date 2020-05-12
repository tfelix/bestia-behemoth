package de.tfelix.bestia.worldgen.noise

import de.tfelix.bestia.worldgen.map.Point

/**
 * Noise provider are responsible for transforming map coordinates into a random
 * noise value. It uses the visitor pattern for type lookup.
 *
 * @author Thomas Felix
 */
interface NoiseProvider {

  /**
   * Returns a random noise value for a Point.
   */
  fun getRandom(coordinate: Point): Double
}
