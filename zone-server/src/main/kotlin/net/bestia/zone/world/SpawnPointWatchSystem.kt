package net.bestia.zone.world

import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component

/** Decides which homes are offered every thirty seconds. Touches no component, only the chunks and the fates. */
@Component
class SpawnPointWatchSystem(
  private val availability: SpawnPointAvailability,
) : System {

  override val phase = Phase.UPKEEP

  override val schedule: Schedule = Schedule.EverySeconds(30f)

  override fun update(world: World, deltaTime: Float) {
    availability.refresh()
  }
}
