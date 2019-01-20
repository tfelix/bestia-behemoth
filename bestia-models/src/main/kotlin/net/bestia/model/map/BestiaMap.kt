package net.bestia.model.map

import net.bestia.model.geometry.Point
import kotlin.collections.Map

/**
 * Helper object to encapsulate map data. Usually the game engine works on this
 * map part objects to provide the pathfinding algorithms with enough
 * information or the AI. Not the whole map is encapsulated inside this map.
 * Usually this is a rather small part because all data is hold in memory and
 * not streamed.
 *
 * Map is basically a wrapper around [MapDataDTO] and contains a bit more
 * data like the tileset information.
 *
 * @author Thomas Felix
 */
class BestiaMap(
    private val data: MapDataDTO,
    private val tilesets: List<Tileset>
) {

  /**
   * Sparse layers on top of the bottom tiles.
   */
  private val tileLayer: Map<Point, Int> = mutableMapOf()

  /**
   * Returns the size (and location) of map which usually only represents a
   * small part of the whole global map.
   *
   * @return The view to this part of the map.
   */
  val rect = data.rect

  /**
   * Returns the walkspeed of a given x and y coordiante. The walkspeed will
   * be 0 if the tile does not exist inside the [Map].
   *
   * @param x
   * X coordinate.
   * @param y
   * Y coordinate.
   * @return The current walkspeed on this tile.
   */
  fun getWalkspeed(x: Long, y: Long): Walkspeed {
    val gid = data.getGroundGid(x, y)

    return if (gid == 0) {
      Walkspeed.ZERO
    } else getTileset(gid)?.let { Walkspeed.fromInt(it.getProperties(gid).walkspeed) }
        ?: Walkspeed.ZERO
  }

  /**
   * Checks if the given point of this map is walkable. If the point is out of
   * range of the selected map an [IndexOutOfBoundsException] is thrown.
   * The given coordinates must be in world space. The coordinates are not
   * relative to this map part.
   *
   * @param p
   * The point to check walkability.
   * @return TRUE if the point is walkable. FALSE otherwise.
   */
  fun isWalkable(p: Point): Boolean {
    if (!data.rect.collide(p)) {
      throw IndexOutOfBoundsException("X or/and Y does not lie inside the map rectangle.")
    }

    val gid = data.getGroundGid(p.x, p.y)
    val groundWalkable = getTileset(gid)?.getProperties(gid)?.isWalkable ?: false

    return if (!groundWalkable) {
      false
    } else tileLayer[p]?.let { layerGid ->
      getTileset(layerGid)
          ?.getProperties(gid)
          ?.isWalkable ?: true
    } ?: false
  }

  /**
   * See [.isWalkable].
   *
   * @param x
   * Position coordiante X
   * @param y
   * Position coordinate Y
   * @return TRUE if the point is walkable. FALSE otherwise.
   */
  fun isWalkable(x: Long, y: Long): Boolean {
    return isWalkable(Point(x, y))
  }

  /**
   * Returns TRUE if the given tile blocks the sight of the player. FALSE if
   * the player can look over the tile.
   *
   * @param p
   * The point to check for sight blocking.
   * @return TRUE if the tile blocks the sight. FALSE otherwise.
   */
  fun blocksSight(p: Point): Boolean {
    if (!data.rect.collide(p)) {
      throw IndexOutOfBoundsException("X or/and Y does not lie inside the map rectangle.")
    }

    val gid = data.getGroundGid(p.x, p.y)
    val groundBlockSight = getTileset(gid)?.getProperties(gid)?.blocksSight ?: false

    return if (!groundBlockSight) {
      false
    } else tileLayer[p]?.let { getTileset(it)?.getProperties(gid)?.blocksSight } ?: false
  }

  /**
   * Gets the tilset for the given gid.
   *
   * @param gid
   * @return The tileset.
   */
  private fun getTileset(gid: Int): Tileset? {
    return tilesets.find { ts -> ts.contains(gid) }
  }
}
