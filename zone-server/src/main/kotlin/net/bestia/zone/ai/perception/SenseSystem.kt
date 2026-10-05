package net.bestia.zone.ai.perception

import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.ai.ecs.AiThrottle
import net.bestia.zone.ai.ecs.SharedMemoryService
import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.TickBuckets
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * The agents' eyes and ears: a periodic sweep over every AI entity that runs each registered [Sense] over
 * it and writes what was noticed onto the right blackboard.
 *
 * It knows nothing about *what* is being sensed. Every sense is an independent `@Component` bean collected
 * by Spring — the same shape `InMessageProcessor` uses for message handlers — so adding "smells a corpse
 * two chunks away" or "hears a fight" is one new class and no change here at all. Today there is one,
 * [ForageSense], which is what finally gave `BestiaDomain.KNOWN_VEGETATION` a writer.
 *
 * ### Cadence
 *
 * Each sense runs for an agent once per [Sense.intervalSeconds], on a tick bucket of its own (see
 * [TickBuckets]), and a lower [net.bestia.zone.ai.ecs.AiDetail] stretches that by its factor. So the cost is
 * flat across ticks instead of a spike every time a period comes round.
 *
 * ### Relationship to [PerceptionSystem]
 *
 * They are not the same thing yet, and the boundary is worth stating rather than guessing at.
 * [PerceptionSystem] is the sole writer of the domain's *observation* keys — position, health, what is
 * hostile and in sight, what time it is — the facts goal availability and combat gate on, refreshed on a
 * fixed fast schedule. This hosts everything else that is learned by looking. Perception is the obvious
 * candidate to become a `SightSense` here, which is why [Sense] is shaped to take it: one bean, its own
 * interval, its own declared reads.
 */
@SpringComponent
@Order(11)
class SenseSystem(
  private val senses: List<Sense>,
  private val sharedMemory: SharedMemoryService,
  private val throttle: AiThrottle,
  zoneConfig: ZoneConfig,
) : System {

  /** Every tick, but each sense runs for an agent only on that agent's own bucket, so no tick takes them all. */
  override val schedule: Schedule = Schedule.EveryTick

  /**
   * Position, plus whatever each sense declares.
   *
   * Folding the senses' own declarations in is what keeps the scheduler honest as senses are added: a sense
   * that reads `Health` makes this system conflict with whatever writes `Health`, without anyone having to
   * remember to widen a set in this file.
   */
  override val reads: ComponentClassSet = setOf(Position::class) + senses.flatMap { it.reads }

  /** Writes the agents' (and their packs') blackboards, so it conflicts with the AI stages by declaration. */
  override val writes: ComponentClassSet = setOf(AiAgent::class)

  /** Each sense's interval in ticks, parallel to [senses]. */
  private val periods = LongArray(senses.size) { (senses[it].intervalSeconds * zoneConfig.tickRate).toLong() }

  override fun update(world: World, deltaTime: Float) {
    val tick = world.tickCount
    val worldMemory = sharedMemory.worldBoard()

    world.query(AiAgent::class, Position::class).each { id ->
      val agent = get<AiAgent>()
      val factor = throttle.factorOf(agent)
      var context: SenseContext? = null

      senses.forEachIndexed { index, sense ->
        // Offset per sense, so one agent's senses do not all fall on the same tick either.
        if (!TickBuckets.isDue(tick, id + index * SENSE_OFFSET, periods[index] * factor)) return@forEachIndexed

        val senseContext = context ?: SenseContext(
          world = world,
          entityId = id,
          agent = agent,
          position = get<Position>().toVec3L(),
          worldMemory = worldMemory,
        ).also { context = it }

        sense.sense(senseContext)
      }
    }
  }

  private companion object {
    const val SENSE_OFFSET = 7_919L
  }
}
