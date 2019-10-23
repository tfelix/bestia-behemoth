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
class Map2DDescription(
    builder: Builder
) : MapDescription {

  private val width = builder.width
  private val height = builder.height
  private val chunkWidth = builder.partWidth
  private val chunkHeight = builder.partHeight
  override val noiseVectorBuilder = builder.noiseVectorBuilder

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

  /**
   * Builder pattern used for the [Map2DDescription]. Use this builder
   * to create an instance of the [MapDescription].
   *
   */
  class Builder(
      var noiseVectorBuilder: NoiseVectorBuilder,
      var width: Long = 0,
      var height: Long = 0,
      var partWidth: Long = 0,
      var partHeight: Long = 0
  ) {
    fun build(): Map2DDescription {
      return Map2DDescription(this)
    }
  }

  override fun toString(): String {
    return "Map2DDescription[chunkWidth: $width, chunkHeight: $height: chunkWidth: $chunkWidth, chunkHeight: $chunkHeight]"
  }
}
