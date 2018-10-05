package net.bestia.zoneserver.map.path

import net.bestia.model.geometry.Point
import net.bestia.model.map.Map
import net.bestia.zoneserver.entity.EntityCollisionService
import org.springframework.stereotype.Component

/**
 * Helper factory in order to provide a clean setup for map pathfinders. This is
 * needed because we need a fresh instance for each invocation of the pathfinder
 * with a correct [Map] instance.
 *
 * @author Thomas Felix
 */
@Component
class MapPathfinderFactory(
    private val entitiyCollisionService: EntityCollisionService
) {

  private val estimator = PointEstimator()

  /**
   * Returns the pathfinder instance to use as the pathfinder.
   *
   * @param gameMap The map to operate on.
   * @return A pathfinder to use in order to lookup paths.
   */
  fun getPathfinder(gameMap: Map): Pathfinder<Point> {
    val nodeProvider = TileNodeProvider(gameMap, entitiyCollisionService)

    return AStarPathfinder(nodeProvider, estimator)
  }
}
