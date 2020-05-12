package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap
import de.tfelix.bestia.worldgen.noise.NoiseProvider

open class GenerateNoiseChunkJob(
    override val name: String,
    private val noiseProvider: NoiseProvider
) : ChunkJob {
  override fun execute(chunk: Chunk, noiseMap: NoiseMap) {
    chunk.getIterator.forEach {
      val noise = noiseProvider.getRandom(it)
      noiseMap.data[it] = noise
    }
  }
}