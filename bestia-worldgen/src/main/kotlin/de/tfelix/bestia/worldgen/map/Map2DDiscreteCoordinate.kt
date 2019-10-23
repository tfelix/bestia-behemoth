package de.tfelix.bestia.worldgen.map

import de.tfelix.bestia.worldgen.random.NoiseProvider

/**
 * Simple two dimensional map coordinates in a discrete manner.
 *
 * @author Thomas Felix
 */
data class Map2DDiscreteCoordinate(
    val x: Long,
    val y: Long
) : MapCoordinate {

  override fun generate(provider: NoiseProvider): Double {
    return provider.getRandom(this)
  }
}