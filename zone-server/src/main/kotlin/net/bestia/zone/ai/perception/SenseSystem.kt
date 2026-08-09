package net.bestia.zone.ai.perception

import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.ai.ecs.SharedMemoryService
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
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
 * The system itself ticks at [BASE_INTERVAL_SECONDS] and each sense is run only when its own
 * [Sense.intervalSeconds] has elapsed, so the base rate is a floor on how often a sense *can* refresh
 * rather than the rate they all pay for. When nothing is due the sweep returns before touching the ECS
 * query at all, which is the common case once senses with different periods coexist.
 *
 * All agents are swept together rather than staggered across ticks the way `AiThinkSystem` staggers
 * planning. That is a deliberate difference of degree: a sense is a lookup and a couple of comparisons,
 * where planning is an A\* search, so the spike a hundred agents make here is not the one worth spreading.
 * If a genuinely expensive sense ever arrives, this is where to spread it.
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
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(BASE_INTERVAL_SECONDS)

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

  /** Seconds since each sense last ran, parallel to [senses]. */
  private val sinceLastRun = FloatArray(senses.size)

  override fun update(world: World, deltaTime: Float) {
    val due = takeDueSenses(deltaTime)
    if (due.isEmpty()) return

    val worldMemory = sharedMemory.worldBoard()

    world.query(AiAgent::class, Position::class).each { id ->
      val agent = get<AiAgent>()
      val context = SenseContext(
        world = world,
        entityId = id,
        agent = agent,
        position = get<Position>().toVec3L(),
        worldMemory = worldMemory,
      )

      due.forEach { it.sense(context) }
    }
  }

  /**
   * Advances every sense's timer and returns those that have come due, resetting their timers.
   *
   * The remainder is carried rather than zeroed, so a sense whose interval is not a multiple of the base
   * rate keeps its average period instead of drifting slower with every run.
   */
  private fun takeDueSenses(deltaTime: Float): List<Sense> {
    var due: MutableList<Sense>? = null

    senses.forEachIndexed { index, sense ->
      val elapsed = sinceLastRun[index] + deltaTime
      if (elapsed < sense.intervalSeconds) {
        sinceLastRun[index] = elapsed
        return@forEachIndexed
      }

      sinceLastRun[index] = elapsed - sense.intervalSeconds
      (due ?: mutableListOf<Sense>().also { due = it }).add(sense)
    }

    return due ?: emptyList()
  }

  companion object {
    /**
     * How often the host wakes up, and therefore the finest interval a sense can ask for.
     *
     * Matched to [PerceptionSystem]'s own rate: a sense that needs to be as quick as sight should be able to
     * be, and nothing needs to be quicker without becoming a system of its own.
     */
    private const val BASE_INTERVAL_SECONDS = 0.5f
  }
}
