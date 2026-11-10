package net.bestia.zone.crafting.repair.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId

/** A master working on a damaged structure, as [net.bestia.zone.prop.ecs.construction.Building] is on a site. */
class Repairing(val targetEntityId: EntityId) : Component {

  /** Health restored that has not added up to a whole point yet. */
  var pendingHp: Float = 0f
}
