package de.tfelix.bestia.worldgen.map

import java.io.Serializable

import de.tfelix.bestia.worldgen.random.NoiseProvider

/**
 * The [MapCoordinate] abstractly describes a coordinate in the world space.
 * It is not known if this is done via descrete values or doubles of floats even the dimension
 * is not known beforehand. Its upon the implementer to create a meaningful coordinate system.
 *
 *
 * It is important to implement [.hashCode] and [.equals] because
 * the [MapCoordinate] is used often as key in hashmaps.
 */
interface MapCoordinate : Serializable {

  /**
   * This will generate the random value of this map coordiante from the given provider.
   * This implements a visitor pattern because the [NoiseProvider] must know the
   * type of the coordinate in order to properly generate the noise.
   *
   * @param provider The noise provider.
   * @return A random value for this given coordiante.
   */
  fun generate(provider: NoiseProvider): Double
}