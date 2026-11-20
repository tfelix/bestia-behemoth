package net.bestia.zone.casting

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Official Guild Approval's sense for the casting slice; the stronghold slice implements it. */
fun interface SeedSensing {

  /** Shows the caster the awake Empyrite Seeds around it. */
  fun sense(world: World, casterId: EntityId)
}
