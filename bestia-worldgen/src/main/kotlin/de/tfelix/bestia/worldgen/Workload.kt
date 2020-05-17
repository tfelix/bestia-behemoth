package de.tfelix.bestia.worldgen

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.pipeline.Pipeline
import mu.KotlinLogging

private val LOG = KotlinLogging.logger { }

data class Workload(
    val identifier: String,
    val noiseMapFactory: NoiseMapFactory,
    val pipeline: Pipeline
) {

  fun execute(chunk: Chunk) {
    LOG.info { "Starting workload '$identifier' for chunk $chunk" }
    val noiseMap = noiseMapFactory.buildNoiseMap(chunk)
    pipeline.execute(noiseMap, chunk)
  }
}