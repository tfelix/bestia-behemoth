package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D
import de.tfelix.bestia.worldgen.noise.NoiseProvider

open class GenerateNoiseMapJob(
    private val noiseProvider: NoiseProvider,
    override val name: String = "Generate noise map"
) : ChunkJob {

  override fun execute(chunk: Chunk, noiseMap: NoiseMap2D): NoiseMap2D {
    chunk.getIterator().forEach {
      val noise = noiseProvider.getRandom(it)
      noiseMap[it] = noise
    }

    return noiseMap
  }
}