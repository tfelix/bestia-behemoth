package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.action.Posture
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.Drive
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.environment.time.BestiaClock
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Moves the appetites that make an agent want anything at all, and advances TTL decay on every blackboard.
 *
 * Without this the goals would be inert: hunger would never cross its threshold, so nothing would ever
 * become available and every creature would stand still. It is the counterpart of the actions that spend
 * what it raises.
 *
 * Which appetites an agent has is the agent's business - see [Drive] - so this knows about no domain and
 * cannot be broken by one adding a fourth. It runs once a second, because a drive changes on the scale of a
 * day rather than a frame, and integrates over the real time the scheduler hands it.
 */
@SpringComponent
@Order(15)
class AiDriveSystem(
  private val sharedMemory: SharedMemoryService,
  private val clock: BestiaClock,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(1f)

  override val writes: ComponentClassSet = setOf(AiAgent::class)

  override fun update(world: World, deltaTime: Float) {
    sharedMemory.tick(deltaTime)

    // Rates are authored per in-game hour, so how much of one a real second is depends on the world clock.
    // `speedFactor` is safe to ask for here where `now()` is not: it reads configuration, while the calendar
    // is anchored to the persisted world row and throws before that row is loaded.
    val gameHoursPerSecond = clock.speedFactor.toFloat() / SECONDS_PER_GAME_HOUR

    world.query(AiAgent::class).each { _ ->
      val agent = get<AiAgent>()
      val memory = agent.memory
      memory.tick(deltaTime)

      val asleep = agent.currentAction()?.posture == Posture.SLEEPING
      val elapsedGameHours = gameHoursPerSecond * deltaTime

      for (drive in agent.drives) {
        val rate = if (asleep) drive.whileSleepingPerGameHour else drive.perGameHour
        adjust(memory, drive, rate * elapsedGameHours)
      }
    }
  }

  /**
   * Moves a 0..100 drive by [amount], in either direction, carrying the fractional part so a rate slower
   * than one point per tick still accumulates instead of being rounded away every time.
   *
   * The carry works in both directions because [Float.toInt] truncates towards zero, so the remainder keeps
   * its sign and a slow fall accumulates just as a slow rise does.
   */
  private fun adjust(memory: Blackboard, drive: Drive, amount: Float) {
    val current = memory.get(drive.key) ?: 0
    val carried = (memory.get(drive.fractionKey) ?: 0f) + amount
    val whole = carried.toInt()

    memory.set(drive.key, (current + whole).coerceIn(0, MAX_DRIVE))
    memory.set(drive.fractionKey, carried - whole)
  }

  companion object {
    private const val MAX_DRIVE = 100

    const val SECONDS_PER_GAME_HOUR = 3_600f
  }
}
