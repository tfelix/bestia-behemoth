package net.bestia.zone.ecs.spawn.ambient

import net.bestia.zone.geometry.Vec3L

/**
 * One resolved place in the wilderness, and what stands there.
 *
 * Immutable and fully determined by the world seed and the lattice cell, which is what lets
 * [AmbientSiteResolver] memoise it and what makes a creature come back to the same tile after a reboot.
 */
data class AmbientSite(
  val cell: Long,
  val bestiaId: Long,
  val position: Vec3L,

  /** False for a species named in `ambient-spawn.never-throttled-profiles`. */
  val throttleable: Boolean
)
