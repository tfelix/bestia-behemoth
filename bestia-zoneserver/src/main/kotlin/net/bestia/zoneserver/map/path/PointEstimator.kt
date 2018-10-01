package net.bestia.zoneserver.map.path

import net.bestia.model.geometry.Point

/**
 * Heuristically estimates the distances of [Point]s.
 *
 * @author Thomas Felix
 */
class PointEstimator : HeuristicEstimator<Point> {

  override fun getDistance(current: Point, target: Point): Float {
    return current.getDistance(target).toFloat()
  }
}
