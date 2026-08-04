package net.bestia.zone.ai.behavior

import net.bestia.zone.ai.Brain
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.navigation.NavigationService

/**
 * Everything a behaviour-tree leaf needs while ticking: the [world], the NPC's own [entityId], its
 * [brain] and the frame [deltaTime]. Leaves read/write ECS state directly through the world (which
 * is already locked by the running act system on the tick thread).
 *
 * [navigation] is threaded through here rather than reached statically because `AiActSystem` already builds
 * one of these per entity per tick and is a Spring bean - so the dependency arrives by the route that already
 * exists, and `Locomotion` stays an object with no injected state of its own.
 *
 * [currentTick] and [tickRate] are here for the same reason: staggering a replan means "not before tick N",
 * and a behaviour tree has no other way to know what tick it is.
 */
class BtContext(
  val world: World,
  val entityId: EntityId,
  val brain: Brain,
  val deltaTime: Float,
  val navigation: NavigationService,
  val currentTick: Long,
  val tickRate: Int
)
