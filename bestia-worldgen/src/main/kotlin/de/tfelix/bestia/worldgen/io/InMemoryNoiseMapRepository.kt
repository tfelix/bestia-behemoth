package de.tfelix.bestia.worldgen.io

import de.tfelix.bestia.worldgen.noise.NoiseMap2D

class InMemoryNoiseMapRepository : NoiseMapRepository {
  private val memoryMap = mutableMapOf<String, NoiseMap2D>()

  override fun delete(identifier: String) {
    memoryMap.remove(identifier)
  }

  override fun save(identifier: String, map: NoiseMap2D) {
    memoryMap[identifier] = map
  }

  override fun load(identifier: String): NoiseMap2D? {
    return memoryMap[identifier]
  }
}
