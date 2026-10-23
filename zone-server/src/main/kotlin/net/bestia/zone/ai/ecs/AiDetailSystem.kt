package net.bestia.zone.ai.ecs

import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.TickBuckets
import net.bestia.zone.ecs.core.World
import net.bestia.zone.movement.ecs.CoarseMovement
import net.bestia.zone.movement.ecs.Position
import org.springframework.stereotype.Component as SpringComponent
import java.util.EnumMap

/**
 * Decides each agent's [AiDetail] once a second, spread over the ticks. The other AI stages only read it.
 *
 * Also lets an agent nobody can see walk in coarse steps, see [CoarseMovement]; its other stages slow down
 * by reading the tier themselves.
 */
@SpringComponent
class AiDetailSystem(
  private val throttle: AiThrottle,
  private val zoneConfig: WorldRulesConfig,
  private val metrics: AiMetrics = AiMetrics(),
) : System {
  override val phase = Phase.AI

  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = setOf(Position::class, PlayerControlled::class, AiThrottleable::class)

  override val writes: ComponentClassSet = setOf(AiAgent::class, CoarseMovement::class)

  /** Agents per level of detail, counted over the sweep in progress and published when it is whole. */
  private val sweep = EnumMap<AiDetail, Int>(AiDetail::class.java)

  override fun update(world: World, deltaTime: Float) {
    val period = zoneConfig.tickRate.toLong()
    val slot = world.tickCount % period
    if (slot == 0L) sweep.clear()

    world.query(AiAgent::class).each { id ->
      if (!TickBuckets.isDue(world.tickCount, id, period)) return@each

      val agent = get<AiAgent>()
      agent.detail = throttle.detailOf(world, id, agent)
      sweep.merge(agent.detail, 1, Int::plus)

      val coarse = agent.detail == AiDetail.BACKGROUND && throttle.factorOf(agent) > 1
      if (coarse == world.has(id, CoarseMovement::class)) return@each

      if (coarse) {
        world.add(id, CoarseMovement(throttle.factorOf(agent).toLong()))
      } else {
        world.remove(id, CoarseMovement::class)
      }
    }

    // Every agent has a bucket of its own among the period's ticks, so after the last of them all have been seen.
    if (slot == period - 1) metrics.agents(sweep)
  }
}
