package net.bestia.zone.ai.behavior

import net.bestia.zone.ai.ecs.Brain
import net.bestia.zone.ecs.Entity
import net.bestia.zone.ecs.ZoneServer

/**
 * Everything a behaviour-tree leaf needs while ticking: the NPC's own [entity] (already held under
 * its write lock by the act system), its [brain], the [zone] (for the single sanctioned foreign
 * write — applying damage to the target), and the frame [deltaTime].
 */
class BtContext(
  val entity: Entity,
  val brain: Brain,
  val zone: ZoneServer,
  val deltaTime: Float
)
