package net.bestia.zone.battle

import net.bestia.zone.geometry.Vec3
import org.springframework.stereotype.Service

/**
 * This service must take into account the terrain and calculate
 * if a direct line of sight is possible.
 */
@Service
class LineOfSightService {

  fun hasLineOfSight(a: Vec3<*>, b: Vec3<*>): Boolean {
    return true
  }
}