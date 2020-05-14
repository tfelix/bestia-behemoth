package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D

/**
 * This job adds a static offset value to the returned noise.
 *
 * @author Thomas Felix
 */
class StaticMultChunkJob(
    private val factor: Double
) : ChunkJob {

  override val name: String
    get() = "Static multiply by $factor"

  override fun execute(chunk: Chunk, noiseMap: NoiseMap2D): NoiseMap2D {
    chunk.getIterator().forEach {
      noiseMap[it] *= factor
    }

    return noiseMap
  }
}
