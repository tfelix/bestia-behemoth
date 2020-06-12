package de.tfelix.bestia.worldgen.pipeline

import de.tfelix.bestia.worldgen.job.ChunkJob
import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D

class SimplePipeline(
    private val chunkJobs: List<ChunkJob>
) : Pipeline {
  constructor(vararg jobs: ChunkJob) : this(jobs.toList())

  override fun execute(noiseMap: NoiseMap2D, chunk: Chunk) {
    chunkJobs.fold(noiseMap, { nm, job ->
      job.execute(chunk, nm)
    })
  }
}