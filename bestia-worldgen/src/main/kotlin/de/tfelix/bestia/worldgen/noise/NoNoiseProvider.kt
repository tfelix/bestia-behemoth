package de.tfelix.bestia.worldgen.noise

import de.tfelix.bestia.worldgen.map.Point
import de.tfelix.bestia.worldgen.noise.NoiseProvider

class NoNoiseProvider : NoiseProvider {
  override fun getRandom(coordinate: Point): Double {
    return 0.0
  }
}