package de.tfelix.bestia.worldgen.io

import de.tfelix.bestia.worldgen.noise.NoiseMap

class InMemoryNoiseMapRepository : NoiseMapRepository {

  private val memoryMap = mutableMapOf<String, NoiseMap>()

  override fun delete(identifier: String) {
    memoryMap.remove(identifier)
  }

  override fun save(identifier: String, map: NoiseMap) {
    memoryMap[identifier] = map
  }

  override fun load(identifier: String): NoiseMap? {
    return memoryMap[identifier]
  }
}
