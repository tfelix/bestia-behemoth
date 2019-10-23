package de.tfelix.bestia.worldgen.map

import java.io.Serializable
import java.util.*

data class Map2DDiscreteInfo(
    val totalWidth: Long,
    val totalHeight: Long
) : Serializable

/**
 * A map part containing discrete values for a two dimensional map. Useful for
 * tilemap creation.
 *
 * @author Thomas Felix
 */
class Map2DDiscreteChunk(
    val x: Long,
    val y: Long,
    val chunkWidth: Long,
    val chunkHeight: Long,
    val info: Map2DDiscreteInfo
) : MapChunk {

  private val area: Long = chunkWidth * chunkHeight

  override val iterator: Iterator<MapCoordinate>
    get() = Map2DIterator()

  override val ident: String
    get() = "mp-$x-$y-$chunkWidth-$chunkHeight"

  /**
   * Helper class to iterate over the all coordinates of this map part.
   */
  private inner class Map2DIterator : Iterator<MapCoordinate> {

    private var currentPosition: Long = 0

    override fun hasNext(): Boolean {
      return currentPosition < area
    }

    override fun next(): Map2DDiscreteCoordinate {
      if (currentPosition > area) {
        throw NoSuchElementException()
      }

      // Regenerate current x and y.
      val cy = y + currentPosition / chunkWidth
      val cx = x + currentPosition % chunkHeight
      currentPosition++

      return Map2DDiscreteCoordinate(cx, cy)
    }
  }

  override fun size(): Long {
    return area
  }

  override fun toGlobalCoordinates(localCords: MapCoordinate): MapCoordinate {
    val local2D = localCords as Map2DDiscreteCoordinate
    val globalX = local2D.x * chunkWidth + localCords.x
    val globalY = local2D.y * chunkHeight + localCords.y

    return Map2DDiscreteCoordinate(globalX, globalY)
  }

  override fun toString(): String {
    return "Map2DDiscreteChunk[$ident]"
  }
}
