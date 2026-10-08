package net.bestia.zone.water

import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component

/** Writes the chunks the water simulation holds out every ten seconds. Touches no component. */
@Component
class WaterPersistSystem(
  private val journal: WaterJournal,
) : System {

  override val phase = Phase.PERSIST

  override val schedule: Schedule = Schedule.EverySeconds(10f)

  override fun update(world: World, deltaTime: Float) {
    journal.flush()
  }
}
