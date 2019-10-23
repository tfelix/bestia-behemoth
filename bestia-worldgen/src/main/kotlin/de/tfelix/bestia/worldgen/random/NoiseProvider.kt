package de.tfelix.bestia.worldgen.random

import de.tfelix.bestia.worldgen.map.Map2DDiscreteCoordinate

/**
 * Noise provider are responsible for transforming map coordinates into a random
 * noise value. It uses the visitor pattern for type lookup.
 *
 * @author Thomas Felix
 */
interface NoiseProvider {

  /**
   * Returns a random noise value for a [Map2DDiscreteCoordinate].
   *
   * @param coordinate
   * The coordinate object to create the noise for.
   * @return The generated random noise value.
   */
  fun getRandom(coordinate: Map2DDiscreteCoordinate): Double
}
