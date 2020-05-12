package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap

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

  override fun execute(chunk: Chunk, noiseMap: NoiseMap): NoiseMap {
    chunk.getIterator.forEach {
      noiseMap[it] = +offset
    }

    return noiseMap
  }
}
