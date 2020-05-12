package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.io.NoiseMapRepository
import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap

class SaveNoiseChunkJob(
    private val noiseIdentifier: String,
    private val repository: NoiseMapRepository
) : ChunkJob {
  override val name: String
    get() = "Save Noise"

  override fun execute(chunk: Chunk, noiseMap: NoiseMap): NoiseMap {
    repository.save(noiseIdentifier, noiseMap)

    return noiseMap
  }
}