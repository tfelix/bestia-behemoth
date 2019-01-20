package net.bestia.model.map

import java.io.Serializable

import com.fasterxml.jackson.annotation.JsonProperty

import net.bestia.model.geometry.Point
import net.bestia.model.geometry.Rect

/**
 * The bestia map consists of multiple small parts called chunks. These parts
 * can be requested by the player client and are delivered to the player via the
 * network. It basically encodes the map data into parts used by the engine.
 * This chunk class is optimized for transfer to the client.
 *
 * @author Thomas Felix
 */
class MapChunk(
    @JsonProperty("p")
    val position: Point,
    groundLayer: IntArray,
    layers: List<Map<Point, Int>>? = null
) : Serializable {

  @JsonProperty("gl")
  val groundLayer = IntArray(MAP_CHUNK_SIZE_AREA)

  @JsonProperty("l")
  val layers: List<Map<Point, Int>> = layers ?: listOf()

  constructor(pos: Point, groundLayer: List<Int>, layers: List<Map<Point, Int>>)
      : this(pos, groundLayer.toIntArray(), layers) {
    // no op.
  }

  init {
    if (groundLayer.size != MAP_CHUNK_SIZE_AREA) {
      throw IllegalArgumentException(
          "Ground layer is not of the size of the chunk. Must be $MAP_CHUNK_SIZE_AREA")
    }

    checkPositiveCords(position)
    System.arraycopy(groundLayer, 0, this.groundLayer, 0, this.groundLayer.size)
  }

  /**
   * Returns the tile gid from the ground.
   *
   * @param chunkPos
   * @return
   */
  fun getGid(chunkPos: Point): Int {
    return getGid(0, chunkPos)
  }

  /**
   * Gets a tile gid from a specific layer.
   *
   * @param layer
   * @param chunkPos
   * @return The tile gid or -1 if the tile is not present.
   */
  fun getGid(layer: Int, chunkPos: Point): Int {
    if (layer < 0) {
      return -1
    }

    if (chunkPos.x < 0 || chunkPos.y < 0) {
      return -1
    }

    return if (layer == 0) {
      groundLayer[(chunkPos.y * MAP_CHUNK_SIZE + chunkPos.x).toInt()]
    } else {
      // Correct the id since the 0 layer (ground) is saved in an own
      // structure.
      val layerData = layers[layer - 1]
      layerData[chunkPos] ?: -1
    }
  }

  companion object {
    /**
     * How many tiles are contained within one of such chunks in each dimension.
     */
    const val MAP_CHUNK_SIZE = 10

    /**
     * The area which is covered by this chunk.
     */
    const val MAP_CHUNK_SIZE_AREA = MAP_CHUNK_SIZE * MAP_CHUNK_SIZE

    /**
     * Calculates the chunk coordinates (these are the coordinates INSIDE the
     * chunk) from the given world coordinates. The given coordinates must be
     * positive.
     *
     * @param world
     * The world coordinates.
     * @return The chunk id in which this point is located.
     */
    fun getChunkCords(world: Point): Point {
      checkPositiveCords(world)
      return Point(world.x % MAP_CHUNK_SIZE, world.y % MAP_CHUNK_SIZE)
    }

    /**
     * Calculates the world coordinates of the given chunk coordinates. The
     * given coordinates must be positive.
     *
     * @param chunk
     * The chunk coordinates.
     * @return The point in world coordinates.
     */
    fun getWorldCords(chunk: Point): Point {
      checkPositiveCords(chunk)
      return Point(chunk.x * MAP_CHUNK_SIZE, chunk.y * MAP_CHUNK_SIZE)
    }

    /**
     * Returns the area in world coordinates which is covered by this chunk. The
     * coordinates must be 0 or positive.
     *
     * @param chunk
     * Chunk coordinates.
     * @return The area/rect which is covered by this chunk.
     */
    fun getWorldRect(chunk: Point): Rect {
      checkPositiveCords(chunk)
      val (x, y) = getWorldCords(chunk)
      return Rect(x, y, MAP_CHUNK_SIZE.toLong(), MAP_CHUNK_SIZE.toLong())
    }

    private fun checkPositiveCords(cords: Point) {
      if (cords.x < 0 || cords.y < 0) {
        throw IllegalArgumentException("Coordinates must be 0 or positive.")
      }
    }
  }
}
