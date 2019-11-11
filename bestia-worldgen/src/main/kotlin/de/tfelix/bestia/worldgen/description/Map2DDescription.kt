package de.tfelix.bestia.worldgen.description

import de.tfelix.bestia.worldgen.map.Map2DDiscreteChunk
import de.tfelix.bestia.worldgen.map.Map2DDiscreteInfo
import de.tfelix.bestia.worldgen.map.MapChunk
import de.tfelix.bestia.worldgen.random.NoiseVectorBuilder

/**
 * This map info implementation describes a discrete two dimensional map
 * usable for tilemap creation.
 *
 * @author Thomas Felix
 */
data class Map2DDescription(
    override val noiseVectorBuilder: NoiseVectorBuilder,
    val width: Long = 0,
    val height: Long = 0,
    val chunkWidth: Long = 0,
    val chunkHeight: Long = 0
) : MapDescription {
  override val mapParts: Iterator<MapChunk>
    get() = Map2DIterator()

  override val mapPartCount: Long
    get() {
      val parts = width / chunkWidth * (height / chunkHeight)
      return if (parts == 0L) 1 else parts
    }

  private inner class Map2DIterator : Iterator<MapChunk> {
    private var i: Long = 0

    override fun hasNext(): Boolean {
      return i < mapPartCount
    }

    override fun next(): MapChunk {
      val curX = i * chunkWidth
      val curY = i * chunkHeight
      i += 1
      return Map2DDiscreteChunk(
          curX,
          curY,
          chunkWidth,
          chunkHeight,
          Map2DDiscreteInfo(width, height)
      )
    }
  }

  override fun toString(): String {
    return "Map2DDescription[chunkWidth: $width, chunkHeight: $height: chunkWidth: $chunkWidth, chunkHeight: $chunkHeight]"
  }
}
