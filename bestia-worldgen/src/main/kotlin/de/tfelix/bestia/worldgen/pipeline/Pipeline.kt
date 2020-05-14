package de.tfelix.bestia.worldgen.pipeline

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D

interface Pipeline {
  fun execute(initialNoiseMap: NoiseMap2D, chunk: Chunk)
}
