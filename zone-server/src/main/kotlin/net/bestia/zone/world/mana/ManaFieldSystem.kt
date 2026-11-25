package net.bestia.zone.world.mana

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Component

/**
 * Lets the mana field creep, leak and drain once a minute. It walks only the tiles play has touched, so an
 * untouched world costs nothing. Touches no component.
 */
@Component
class ManaFieldSystem(
  private val field: ManaField,
  private val ground: ManaGround,
  config: ManaFieldConfig,
  clock: BestiaClock,
) : System {

  private val rule = ManaFieldRule(config, realSecondsPerBestiaDay = SECONDS_PER_DAY / clock.speedFactor)

  override val phase = Phase.UPKEEP

  override val schedule: Schedule = Schedule.EverySeconds(60f)

  override val reads: ComponentClassSet = emptySet()

  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    rule.step(field, ground, deltaTime.toDouble())
  }

  private companion object {
    const val SECONDS_PER_DAY = 86_400.0
  }
}
