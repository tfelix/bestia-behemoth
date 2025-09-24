package net.bestia.zone.navigation

import net.bestia.zone.geometry.Vec3L
import org.springframework.stereotype.Service

@Service
class NavigationService(
  private val navGridFactory: NavGridFactory
) {

  private val pathFinder = AStarPathfinder()

  fun findPath(
    x: Long,
    y: Long,
    width: Long,
    height: Long,
    start: Vec3L,
    goal: Vec3L
  ) {
    // which algo to use? atm only A3

    // load data and feed into the NavGridFactory, cache the generated nav grid for later re-use as long as
    // the voxel did not update.

    //
  }
}