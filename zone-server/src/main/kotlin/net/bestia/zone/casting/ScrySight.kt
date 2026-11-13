package net.bestia.zone.casting

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/** A Sage's open Scry, on the caster: where the sight looks, and the eye that floats there. */
class ScrySight(
  val centre: Vec3L,
  val eyeId: EntityId,
) : Component {

  /** Set by the first of the several ways a sight can end, so the others find nothing left to do. */
  var isEnded: Boolean = false
}
