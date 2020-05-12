package de.tfelix.bestia.worldgen.io

import de.tfelix.bestia.worldgen.noise.NoiseMap

interface NoiseMapRepository {
  fun delete(identifier: String)
  fun save(identifier: String, map: NoiseMap)
  fun load(identifier: String): NoiseMap?
}