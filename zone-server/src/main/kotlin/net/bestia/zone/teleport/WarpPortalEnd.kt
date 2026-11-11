package net.bestia.zone.teleport

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/**
 * One end of a Sage's warp portal. Both ends share one [Passages], so the portal lets so many bodies through in all,
 * whichever way they go.
 */
class WarpPortalEnd(
  val otherEnd: EntityId,
  /** Where a body that steps in here comes out: beside the other end. */
  val exit: Vec3L,
  val passages: Passages,
  var remainingSeconds: Float,
) : Component {

  class Passages(var left: Int)
}
