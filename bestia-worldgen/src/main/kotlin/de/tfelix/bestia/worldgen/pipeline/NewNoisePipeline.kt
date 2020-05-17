package de.tfelix.bestia.worldgen.pipeline

import de.tfelix.bestia.worldgen.NoiseMapFactory
import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D

/**
 * Creates new noise maps for each sub-pipeline.
 */
class NewNoisePipeline(
    private val noiseFactory: NoiseMapFactory,
    private val pipelines: List<Pipeline>
) : Pipeline {
  constructor(noiseFactory: NoiseMapFactory, vararg pipelines: Pipeline) : this(noiseFactory, pipelines.toList())

  override fun execute(noiseMap: NoiseMap2D, chunk: Chunk) {
    pipelines.forEach {
      val newNoise = noiseFactory.buildNoiseMap(chunk)
      it.execute(newNoise, chunk)
    }
  }
}