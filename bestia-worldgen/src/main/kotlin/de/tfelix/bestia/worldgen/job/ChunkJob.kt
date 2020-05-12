package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap

interface ChunkJob {
  val name: String
  fun execute(chunk: Chunk, noiseMap: NoiseMap): NoiseMap
}