package net.bestia.zoneserver.map.path

import mu.KotlinLogging
import net.bestia.model.geometry.Point
import net.bestia.model.map.BestiaMap
import net.bestia.zoneserver.entity.EntityCollisionService
import java.util.*

private val LOG = KotlinLogging.logger { }

/**
 * Provides tile based nodes for map pathfinding based on a map object. The
 * whole path must be covered by the returned map object otherwise no new nodes
 * can be delivered.
 *
 * When delivering the nodes it must also take the walkpeed (walking cost) into
 * account.
 *
 * @author Thomas Felix
 */
class TileNodeProvider(
    private val gameMap: BestiaMap,
    private val entityCollisionService: EntityCollisionService
) : NodeProvider<Point> {

  override fun getConnectedNodes(node: Node<Point>): Set<Node<Point>> {

    if (!gameMap.rect.collide(node.self)) {
      return emptySet()
    }

    val p = node.self
    val connections = HashSet<Node<Point>>()

    var x: Long
    var y: Long

    // Iterate over all possible connections. We would have to check dynamic
    // connections like entities blocking the way. This has to be done.

    // Left position.
    x = p.x - 1
    y = p.y
    checkWalkable(connections, x, y)

    // Top left position.
    x = p.x - 1
    y = p.y - 1
    checkWalkable(connections, x, y)

    // Top position.
    x = p.x
    y = p.y - 1
    checkWalkable(connections, x, y)

    // Top right position.
    x = p.x + 1
    y = p.y - 1
    checkWalkable(connections, x, y)

    // right position.
    x = p.x + 1
    y = p.y
    checkWalkable(connections, x, y)

    // right bottom position.
    x = p.x + 1
    y = p.y + 1
    checkWalkable(connections, x, y)

    // bottom position.
    x = p.x
    y = p.y + 1
    checkWalkable(connections, x, y)

    // bottom left position.
    x = p.x - 1
    y = p.y + 1
    checkWalkable(connections, x, y)

    LOG.trace("Walkable neighbours for {} are: {}", node.toString(), connections.toString())

    return connections
  }

  private fun checkWalkable(connections: MutableSet<Node<Point>>, x: Long, y: Long) {
    if (isMapWalkable(x, y) && isEntityWalkable(x, y)) {

      val pos = Point(x, y)
      val temp = Node(pos)

      // Calculate the cost of the tilemap. Must be the inverse (lower
      // walkspeed means higher walking cost).
      val slowestWalkspeed = Math.min(getMapCost(pos), getEntityCost(pos))
      temp.ownCost = 1 / slowestWalkspeed

      connections.add(temp)
    }
  }

  private fun getMapCost(p: Point): Float {
   return gameMap.getWalkspeed(p.x, p.y).speed
  }

  private fun getEntityCost(p: Point): Float {
    // FIXME berechnen.
    return 1f
  }

  /**
   * Checks if the map itself is walkable.
   *
   * @param x
   * X cord
   * @param y
   * Y cord
   * @return TRUE if the map is walkable, FALSE otherwise.
   */
  private fun isMapWalkable(x: Long, y: Long): Boolean {
    return gameMap.isWalkable(x, y)
  }

  /**
   * Checks if an entity blocks the way.
   *
   * @param x
   * X cord
   * @param y
   * y cord
   * @return TRUE if no entity blocks the walking. FALSE otherwise.
   */
  private fun isEntityWalkable(x: Long, y: Long): Boolean {
    val position = Point(x, y)

    return entityCollisionService.getAllCollidingEntityIds(position).isEmpty()
  }
}
