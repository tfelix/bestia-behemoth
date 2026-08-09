package net.bestia.zone.ai.perception

import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.MemoryScope
import net.bestia.zone.ai.core.state.StateKey
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/**
 * One way of noticing something about the world, run over every agent by [SenseSystem].
 *
 * A sense turns some part of the live world into a fact on a blackboard, and that is the whole of its
 * contract — it does not decide anything, and nothing about goals or plans belongs in one. Sight, smell,
 * hearing, noticing the ground you are standing on: each is a separate implementation, and adding one is a
 * new `@Component` bean rather than a new ECS system, a new scheduler wave and a new set of read/write
 * declarations to keep honest.
 *
 * ### Why they share one system
 *
 * Every sense wants the same thing — a periodic sweep over all agents with each agent's position and memory
 * in hand — and each one that got its own [net.bestia.zone.ecs.core.System] would repeat that sweep and take
 * its own wave in the scheduler. Hosting them lets the query happen once for all of them, and lets a sense
 * that has nothing to do this tick cost nothing at all.
 */
interface Sense {

  /** Short identifier, for logging and for telling two senses apart in a debug dump. */
  val name: String

  /**
   * How often this sense refreshes.
   *
   * Per sense rather than per system because senses genuinely differ: what is in front of a creature can
   * change between one step and the next, while what the ground is made of does not change at all. Making
   * every sense run at the rate the fastest one needs is how a cheap sweep turns expensive.
   */
  val intervalSeconds: Float

  /**
   * ECS components this sense reads, folded into [SenseSystem]'s own declaration.
   *
   * A sense that reaches for a component without naming it here is exactly the mistake the scheduler cannot
   * catch — see `World`'s note on `reads`/`writes` being the whole contract. [SenseContext.position] and the
   * agent itself are already covered by the host system, so most senses leave this empty.
   */
  val reads: ComponentClassSet get() = emptySet()

  fun sense(context: SenseContext)
}

/**
 * What a [Sense] is handed for one agent on one sweep: the [world] it can look into, the [agent] doing the
 * looking, and where it is.
 *
 * Modelled on `BtContext` and for the same reason — a sense that needs a service takes it as a constructor
 * argument and is handed one by Spring, rather than every sense being able to reach everything through a
 * context that grows to accommodate the greediest of them.
 */
class SenseContext(
  val world: World,
  val entityId: EntityId,
  val agent: AiAgent,
  /** Where the agent is standing, already resolved from its `Position`. */
  val position: Vec3L,
  private val worldMemory: Blackboard,
) {

  /**
   * Records [value] on whichever board [key]'s [MemoryScope] says it belongs on.
   *
   * The routing lives here rather than in each sense on purpose: "team board, or my own if I have no pack"
   * is the sort of two-line idiom that gets copied slightly wrong the third time, and getting it wrong means
   * a fact silently stops being shared. This is the same cascade [net.bestia.zone.ai.core.planner.EffectWriteBack]
   * applies to an action's effects, so a fact means the same thing however it was learned.
   */
  fun <T> remember(key: StateKey<T>, value: T, retain: Float = Blackboard.DEFAULT_RETAIN_TIME_SECONDS) {
    boardFor(key.scope).set(key, value, retain)
  }

  /** Reads back what [remember] would have written — the board the key lives on, not a merged view. */
  fun <T> recall(key: StateKey<T>): T? = boardFor(key.scope).get(key)

  /** Forgets [key] on the board it lives on, for a sense whose observations contradict a standing fact. */
  fun forget(key: StateKey<*>) {
    boardFor(key.scope).remove(key)
  }

  private fun boardFor(scope: MemoryScope): Blackboard = when (scope) {
    MemoryScope.INDIVIDUAL -> agent.memory
    // A loner has no pack board and must not be handed one; its own memory is where a team-scoped fact goes.
    MemoryScope.TEAM -> agent.teamMemory ?: agent.memory
    MemoryScope.WORLD -> worldMemory
  }
}
