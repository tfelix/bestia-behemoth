package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.io.NoiseMapRepository
import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D

class DeleteNoiseChunkJob(
    private val noiseIdentifier: String,
    private val repository: NoiseMapRepository
) : ChunkJob {
  override val name: String
    get() = "Delete Noise"

  override fun execute(chunk: Chunk, noiseMap: NoiseMap2D): NoiseMap2D {
    repository.delete(noiseIdentifier)

    return noiseMap
  }
}