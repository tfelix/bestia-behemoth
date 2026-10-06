package net.bestia.zone.casting

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/** Charts the ground around a point for a master. The cartography slice implements it. */
interface Surveying {

  /** @param centre the aimed-at point in voxels, as a skill target position always is */
  fun survey(masterId: Long, accountId: Long?, entityId: EntityId, centre: Vec3L, radiusMetres: Double)
}
