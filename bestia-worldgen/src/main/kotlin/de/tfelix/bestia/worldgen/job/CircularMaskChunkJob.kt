package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.map.Point
import de.tfelix.bestia.worldgen.noise.NoiseMap2D

/**
 * Adds a circular mask to the output.
 */
class CircularMaskChunkJob(
    private val radius: Double,
    private val border: Double,
    private val center: Point
) : ChunkJob {

  override val name: String
    get() = "Apply circular mask"

  override fun execute(chunk: Chunk, noiseMap: NoiseMap2D): NoiseMap2D {
    chunk.getIterator().forEach {
      val glob = chunk.toGlobalCoordinates(it)
      val d = glob.distance(center)

      val f = when {
        d < radius - border -> 1.0
        d < radius -> 1.0 - ((d - (radius - border)) / border)
        else -> 0.0
      }
      noiseMap[it] *= f
    }

    return noiseMap
  }
}
