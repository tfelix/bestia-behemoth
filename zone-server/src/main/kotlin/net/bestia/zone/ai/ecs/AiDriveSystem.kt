package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.StateKey
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Raises the drives that make a bestia want anything at all — hunger, tiredness and restlessness — and
 * advances TTL decay on every blackboard.
 *
 * Without this the domain's goals would be inert: hunger would never cross its threshold, so
 * `EatVegetation` would never become available, and the creature would stand still forever. It is the
 * counterpart of the actions that spend those drives.
 *
 * ### Why restlessness exists
 *
 * It is what turns idling into an ordinary goal. Wandering has no naturally unsatisfiable state, so the
 * previous code reached for a reflexive fallback outside the goal system to get a mob to move at all.
 * Modelling boredom as just another rising drive removes that special case entirely: it climbs while the
 * creature has nothing better to do, the wander goal becomes available and genuinely unsatisfied, a bout
 * of ambling spends it, and it climbs again.
 *
 * Runs once a second — drives change on the timescale of a creature's day, not a frame — and integrates
 * over the real elapsed time the scheduler hands it, so the rates below are per second regardless of
 * cadence.
 */
@SpringComponent
@Order(15)
class AiDriveSystem(
  private val sharedMemory: SharedMemoryService,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(1f)

  override val writes: ComponentClassSet = setOf(AiAgent::class)

  override fun update(world: World, deltaTime: Float) {
    sharedMemory.tick(deltaTime)

    world.query(AiAgent::class).each { _ ->
      val memory = get<AiAgent>().memory
      memory.tick(deltaTime)

      raise(memory, BestiaDomain.HUNGER, HUNGER_PER_SECOND * deltaTime)
      raise(memory, BestiaDomain.TIREDNESS, TIREDNESS_PER_SECOND * deltaTime)
      raise(memory, BestiaDomain.RESTLESSNESS, RESTLESSNESS_PER_SECOND * deltaTime)
    }
  }

  /**
   * Nudges a 0..100 drive upwards, carrying the fractional part so a rate slower than one point per tick
   * still accumulates instead of being rounded away to nothing every time.
   */
  private fun raise(memory: Blackboard, key: StateKey<Int>, amount: Float) {
    val current = memory.get(key) ?: 0
    val fractionKey = fractionKeys.getValue(key)
    val carried = (memory.get(fractionKey) ?: 0f) + amount

    val whole = carried.toInt()
    if (whole > 0) {
      memory.set(key, (current + whole).coerceAtMost(MAX_DRIVE), Blackboard.PERMANENT)
      memory.set(fractionKey, carried - whole, Blackboard.PERMANENT)
    } else {
      // Drives must survive the blackboard's TTL sweep, hence PERMANENT: a hunger that quietly expired
      // would reset the creature's appetite every ten minutes.
      memory.set(key, current, Blackboard.PERMANENT)
      memory.set(fractionKey, carried, Blackboard.PERMANENT)
    }
  }

  companion object {
    private const val MAX_DRIVE = 100

    /** Roughly: peckish in ~3 minutes, sleepy in ~7, bored in ~1. */
    private const val HUNGER_PER_SECOND = 0.55f
    private const val TIREDNESS_PER_SECOND = 0.25f
    private const val RESTLESSNESS_PER_SECOND = 1.6f

    /**
     * Sub-integer remainders, kept beside each drive rather than inside it so the drives stay plain Ints
     * that the utility curves and preconditions can read without knowing about accumulation.
     */
    private val fractionKeys = mapOf(
      BestiaDomain.HUNGER to StateKey<Float>("hungerFraction"),
      BestiaDomain.TIREDNESS to StateKey<Float>("tirednessFraction"),
      BestiaDomain.RESTLESSNESS to StateKey<Float>("restlessnessFraction"),
    )
  }
}
