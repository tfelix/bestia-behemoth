package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.io.NoiseMapRepository
import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D
import java.lang.IllegalStateException

/**
 * Adds this noisemap on top of the incoming noise map.
 *
 * @author Thomas Felix
 */
class AddChunkJob(
    private val addNoiseMapIdentifier: String,
    private val noiseMapRepository: NoiseMapRepository
) : ChunkJob {

  override val name: String
    get() = "Add $addNoiseMapIdentifier"

  private val loadedNoiseMap by lazy {
    noiseMapRepository.load(addNoiseMapIdentifier)
  }

  override fun execute(chunk: Chunk, noiseMap: NoiseMap2D): NoiseMap2D {
    chunk.getIterator().forEach {
      noiseMap[it] += loadedNoiseMap[it]
    }

    return noiseMap
  }
}
