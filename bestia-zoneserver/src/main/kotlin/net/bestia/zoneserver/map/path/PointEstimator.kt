package net.bestia.zoneserver.map.path

import net.bestia.model.geometry.Vec3

/**
 * Heuristically estimates the distances of [Vec3]s.
 *
 * @author Thomas Felix
 */
class PointEstimator : HeuristicEstimator<Vec3> {

  override fun getDistance(current: Vec3, target: Vec3): Float {
    return current.getDistance(target).toFloat()
  }
}
