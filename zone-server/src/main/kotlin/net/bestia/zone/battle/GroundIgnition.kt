package net.bestia.zone.battle

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/** Lets something burning set the ground below it alight, without battle reaching up into the ground slice. */
fun interface GroundIgnition {

  fun ignite(at: Vec3L, sourceEntityId: EntityId)

  companion object {
    val NONE = GroundIgnition { _, _ -> }
  }
}
