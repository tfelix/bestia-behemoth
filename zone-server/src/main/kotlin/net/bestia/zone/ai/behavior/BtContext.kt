package net.bestia.zone.ai.behavior

import net.bestia.zone.ai.ecs.Brain
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.World

/**
 * Everything a behaviour-tree leaf needs while ticking: the [world], the NPC's own [entityId], its
 * [brain] and the frame [deltaTime]. Leaves read/write ECS state directly through the world (which
 * is already locked by the running act system on the tick thread).
 */
class BtContext(
  val world: World,
  val entityId: EntityId,
  val brain: Brain,
  val deltaTime: Float
)
