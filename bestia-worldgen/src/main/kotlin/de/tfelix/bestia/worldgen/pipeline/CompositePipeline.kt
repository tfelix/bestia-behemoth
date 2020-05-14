package de.tfelix.bestia.worldgen.pipeline

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D

class CompositePipeline(
    private val pipelines: List<Pipeline>
) : Pipeline {
  constructor(vararg pipelines: Pipeline) : this(pipelines.toList())

  override fun execute(initialNoiseMap: NoiseMap2D, chunk: Chunk) {
    pipelines.forEach { it.execute(initialNoiseMap, chunk) }
  }
}