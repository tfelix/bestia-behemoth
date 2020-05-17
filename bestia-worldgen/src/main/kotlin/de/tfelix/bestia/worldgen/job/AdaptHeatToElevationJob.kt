package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.io.NoiseMapRepository
import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D

class AdaptHeatToElevationJob(
    private val heightMapIdentifier: String,
    private val repository: NoiseMapRepository
) : ChunkJob {
  override val name: String
    get() = "Save Noise"

  override fun execute(chunk: Chunk, noiseMap: NoiseMap2D): NoiseMap2D {
    val heightMap = repository.load(heightMapIdentifier)

    chunk.getIterator().forEach {
      noiseMap[it] = noiseMap[it] - 0.9 * heightMap[it]
    }

    return noiseMap
  }
}