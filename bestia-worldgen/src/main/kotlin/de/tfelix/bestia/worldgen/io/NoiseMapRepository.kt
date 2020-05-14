package de.tfelix.bestia.worldgen.io

import de.tfelix.bestia.worldgen.noise.NoiseMap2D

interface NoiseMapRepository {
  fun delete(identifier: String)
  fun save(identifier: String, map: NoiseMap2D)
  fun load(identifier: String): NoiseMap2D?
}