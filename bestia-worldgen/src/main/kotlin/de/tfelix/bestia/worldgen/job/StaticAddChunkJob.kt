package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D

/**
 * This job adds a static offset value to the returned noise.
 *
 * @author Thomas Felix
 */
class StaticAddChunkJob(
    private val offset: Double
) : ChunkJob {

  override val name: String
    get() = "Static add $offset"

  override fun execute(chunk: Chunk, noiseMap: NoiseMap2D): NoiseMap2D {
    chunk.getIterator().forEach {
      noiseMap[it] += offset
    }

    return noiseMap
  }
}
