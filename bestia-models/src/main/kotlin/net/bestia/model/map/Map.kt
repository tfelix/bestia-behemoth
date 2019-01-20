package net.bestia.model.map

import java.util.ArrayList
import java.util.Collections
import java.util.Objects
import java.util.Optional

import net.bestia.model.geometry.Point
import net.bestia.model.geometry.Rect

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
class Map(data: MapDataDTO, tilesets: List<Tileset>) {

  private val tilesets: List<Tileset>
  private val data: MapDataDTO

  /**
   * Sparse layers on top of the bottom tiles.
   */
  private val tileLayer = ArrayList<Map<Point, Int>>()

  /**
   * Returns the size (and location) of map which usually only represents a
   * small part of the whole global map.
   *
   * @return The view to this part of the map.
   */
  val rect: Rect
    get() = data.rect

  init {

    this.data = Objects.requireNonNull(data)
    this.tilesets = Collections.unmodifiableList(ArrayList(tilesets))

  }

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

    // Find the walkspeed on the tile.
    val gid = data.getGroundGid(x, y)

    return if (gid == 0) {
      Walkspeed.fromInt(0)
    } else getTileset(gid).map { ts -> Walkspeed.fromInt(ts.getProperties(gid).walkspeed) }
        .orElse(Walkspeed.fromInt(0))

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

    if (gid == 0) {
      return false
    }

    val groundWalkable = getTileset(gid).map { ts -> ts.getProperties(gid).isWalkable }.orElse(false)

    return if (!groundWalkable) {
      false
    } else tileLayer.stream().filter { d -> d.containsKey(p) }.map<Int> { d -> d.get(p) }.filter { layerGid -> getTileset(layerGid!!).map { ts -> ts.getProperties(gid).isWalkable }.orElse(true) }.findAny().map { data -> false }.orElse(true)

    // Now we must check the layers above it.

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

    if (gid == 0) {
      return false
    }

    val groundBlockSight = getTileset(gid).map { ts -> ts.getProperties(gid).blockSight() }.orElse(false)

    return if (!groundBlockSight) {
      false
    } else tileLayer.stream()
        .filter { layer -> layer.containsKey(p) }
        .map<Int> { layer -> layer.get(p) }
        .map<Optional<Tileset>>(Function<Int, Optional<Tileset>> { getTileset(it) })
        .anyMatch { tileset -> tileset.isPresent && tileset.get().getProperties(gid).blockSight() }
  }

  /**
   * Gets the tilset for the given gid.
   *
   * @param gid
   * @return The tileset.
   */
  private fun getTileset(gid: Int): Optional<Tileset> {

    return tilesets.stream().filter { ts -> ts.contains(gid) }.findAny()
  }
}
