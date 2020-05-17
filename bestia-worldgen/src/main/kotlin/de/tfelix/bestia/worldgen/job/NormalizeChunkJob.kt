package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D
import kotlin.math.max
import kotlin.math.min

/**
 * Normalizes the range in the noise between 0.0 - 1.0
 *
 * @author Thomas Felix
 */
class NormalizeChunkJob : ChunkJob {

  override val name: String
    get() = "Normalize"

  override fun execute(chunk: Chunk, noiseMap: NoiseMap2D): NoiseMap2D {
    val min = noiseMap.map { it.second }.min() ?: 0.0
    val max = noiseMap.map { it.second }.max() ?: 1.0

    noiseMap.forEach {
      noiseMap[it.first] = (it.second - min) / max
    }

    return noiseMap
  }
}
