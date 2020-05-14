package de.tfelix.bestia.worldgen.pipeline

import de.tfelix.bestia.worldgen.job.ChunkJob
import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D

class SimplePipeline(
    private val chunkJobs: List<ChunkJob>
) : Pipeline {
  constructor(vararg jobs: ChunkJob) : this(jobs.toList())

  // TODO NoiseMap ggf durch eine Factory ersetzen?
  override fun execute(initialNoiseMap: NoiseMap2D, chunk: Chunk) {
    val copiedNoiseMap = initialNoiseMap.createNew()
    chunkJobs.fold(copiedNoiseMap, { noiseMap, job ->
      job.execute(chunk, noiseMap)
    })
  }
}