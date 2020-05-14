package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D

open class ZeroNoiseMapJob(
    override val name: String = "Set noise map to zero"
) : ChunkJob {

  override fun execute(chunk: Chunk, noiseMap: NoiseMap2D): NoiseMap2D {
    chunk.getIterator().forEach {
      noiseMap[it] = 0.0
    }

    return noiseMap
  }
}