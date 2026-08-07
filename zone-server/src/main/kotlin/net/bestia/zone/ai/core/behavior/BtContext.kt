package net.bestia.zone.ai.core.behavior

import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * What a behaviour-tree node knows about the tick it is being ticked on: the [world], the acting
 * [entityId], its live [memory] and the [state] snapshot the current plan was made from, plus the
 * frame [deltaTime] and where we are in the tick sequence.
 *
 * Leaves read/write ECS state directly through [world], which is already locked by the act system
 * running on the tick thread.
 *
 * ### Tick facts only — services arrive through the leaf
 *
 * This deliberately carries no game services. It used to thread `NavigationService` through, which
 * meant every leaf could reach every service whether it needed it or not, and `Locomotion` had to
 * pretend to be stateless while borrowing a dependency from its caller. Now a leaf that needs a
 * service takes it as a constructor argument, and the `ActionTemplate` that grounds the action
 * supplies it — templates are built by the domain, which is where Spring beans are available. A leaf
 * therefore declares its own dependencies and is trivially testable with fakes.
 *
 * [currentTick] and [tickRate] are here because staggering work means "not before tick N", and a
 * behaviour tree has no other way to know what tick it is.
 */
class BtContext(
  val world: World,
  val entityId: EntityId,
  /** The agent's own live memory — the place a leaf records what it observed. */
  val memory: Blackboard,
  /** Frozen world+team+individual snapshot the current plan was built from. */
  val state: WorldState,
  val deltaTime: Float,
  val currentTick: Long,
  val tickRate: Int,
)
