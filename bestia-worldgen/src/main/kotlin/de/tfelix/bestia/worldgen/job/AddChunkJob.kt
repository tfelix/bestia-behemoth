package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.io.NoiseMapRepository
import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap
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
        ?: throw IllegalStateException("Noise map with identifier '$addNoiseMapIdentifier' was not found")
  }

  override fun execute(chunk: Chunk, noiseMap: NoiseMap): NoiseMap {
    chunk.getIterator.forEach {
      noiseMap[it] += loadedNoiseMap[it]
    }

    return noiseMap
  }
}
