package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D
import kotlin.math.pow

/**
 * Job dos a make the exponent. It is useful for compressing the height map.
 *
 * @author Thomas Felix
 */
class PowerOfChunkJob(
    private val exp: Double
) : ChunkJob {

  override val name: String
    get() = "Power of $exp job"

  override fun execute(chunk: Chunk, noiseMap: NoiseMap2D): NoiseMap2D {
    chunk.getIterator().forEach {
      noiseMap[it] = noiseMap[it].pow(exp)
      // Small values can lead to a NaN result. we clamp this to 0.
      if (noiseMap[it].isNaN()) {
        noiseMap[it] = 0.0
      }
    }

    return noiseMap
  }
}
