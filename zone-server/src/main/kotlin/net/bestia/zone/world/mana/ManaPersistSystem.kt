package net.bestia.zone.world.mana

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component

/**
 * Writes the mana field out every five minutes, and once more at shutdown. The field moves over days, so a
 * crash loses at most a few minutes of it. Touches no component.
 */
@Component
class ManaPersistSystem(
  private val journal: ManaJournal,
) : System {

  override val phase = Phase.PERSIST

  override val schedule: Schedule = Schedule.EverySeconds(300f)

  override val reads: ComponentClassSet = emptySet()

  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    journal.flush()
  }
}
