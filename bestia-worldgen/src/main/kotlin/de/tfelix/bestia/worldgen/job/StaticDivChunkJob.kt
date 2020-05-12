package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap

/**
 * This job adds a static offset value to the returned noise.
 *
 * @author Thomas Felix
 */
class StaticDivChunkJob(
    private val divider: Double
) : ChunkJob {

  override val name: String
    get() = "Static divide by $divider"

  override fun execute(chunk: Chunk, noiseMap: NoiseMap): NoiseMap {
    chunk.getIterator.forEach {
      noiseMap[it] /= divider
    }

    return noiseMap
  }
}
