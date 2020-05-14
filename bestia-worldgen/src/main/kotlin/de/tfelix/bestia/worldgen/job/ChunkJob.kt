package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D

interface ChunkJob {
  val name: String
  fun execute(chunk: Chunk, noiseMap: NoiseMap2D): NoiseMap2D
}