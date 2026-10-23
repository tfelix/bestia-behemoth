package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.CommonKeys
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.travel.Journey
import net.bestia.zone.travel.JourneyOrder
import net.bestia.zone.util.EntityId

/**
 * When a creature's own AI keeps its hands off: a player drives it, or it is on a journey a player ordered.
 * The bestia keeps walking even when attacked; its stance and aggression decide nothing until it arrives.
 */
object PlayerHold {

  val READS: ComponentClassSet = setOf(PlayerControlled::class, Journey::class)

  fun isHeld(world: World, id: EntityId): Boolean {
    return world.has(id, PlayerControlled::class) || world.get(id, Journey::class)?.order == JourneyOrder.PLAYER
  }

  /** Home becomes where the player left it, so it does not walk back to where it used to live. */
  fun settleHome(agent: AiAgent, position: Vec3L) {
    agent.memory.set(CommonKeys.HOME_POSITION, position, Blackboard.PERMANENT)
  }
}
