package de.tfelix.bestia.worldgen.random

import de.tfelix.bestia.worldgen.map.Map2DDiscreteCoordinate

class EmptyProvider : NoiseProvider {
  override fun getRandom(coordinate: Map2DDiscreteCoordinate): Double {
    return 0.0
  }
}