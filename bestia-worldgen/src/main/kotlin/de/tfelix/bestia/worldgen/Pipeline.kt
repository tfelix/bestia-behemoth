package de.tfelix.bestia.worldgen

import de.tfelix.bestia.worldgen.job.ChunkJob
import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap

interface Pipeline {
  fun execute(initialNoiseMap: NoiseMap, chunk: Chunk)
}

class SimplePipeline(
    private val chunkJobs: List<ChunkJob>
) : Pipeline {
  constructor(vararg jobs: ChunkJob) : this(jobs.toList())

  override fun execute(initialNoiseMap: NoiseMap, chunk: Chunk) {
    chunkJobs.foldRight(initialNoiseMap, { job, noiseMap ->
      job.execute(chunk, noiseMap)
    })
  }
}

class ParallelPipeline(
    private val piplines: List<Pipeline>
) : Pipeline {
  constructor(vararg pipelines: Pipeline) : this(pipelines.toList())

  override fun execute(initialNoiseMap: NoiseMap, chunk: Chunk) {
    TODO("Not yet implemented")
  }
}

