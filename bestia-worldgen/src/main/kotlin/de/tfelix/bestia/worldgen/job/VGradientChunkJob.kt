package de.tfelix.bestia.worldgen.job

import de.tfelix.bestia.worldgen.map.Chunk
import de.tfelix.bestia.worldgen.noise.NoiseMap2D
import kotlin.math.abs

/**
 * Adds a vertical gradient to the map from top to bottom
 */
class VGradientChunkJob(
    private val border: Double,
    private val height: Long
) : ChunkJob {

  private val cy = height / 2

  init {
    require(height > border) { "height must be bigger then the border size" }
  }

  override val name: String
    get() = "apply vertical gradient mask"

  override fun execute(chunk: Chunk, noiseMap: NoiseMap2D): NoiseMap2D {
    chunk.getIterator().forEach {
      val glob = chunk.toGlobalCoordinates(it)
      val d = abs(cy - glob.y)

      val f = (1.0 - (d - cy + border) / border).coerceIn(0.0, 1.0)

      noiseMap[it] *= f
    }

    return noiseMap
  }
}
