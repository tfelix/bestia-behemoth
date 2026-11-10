package net.bestia.zone.ai.domain.bestia

import net.bestia.zone.geometry.Vec3L

/** Where a bestia under orders works: a circle around the home it was given the job at. */
data class WorkArea(val center: Vec3L, val radius: Long) {

  fun contains(position: Vec3L): Boolean {
    return center.distance(position) <= radius
  }
}
