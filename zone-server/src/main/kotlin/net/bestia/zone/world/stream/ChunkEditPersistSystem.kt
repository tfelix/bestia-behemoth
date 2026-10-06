package net.bestia.zone.world.stream

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component

/** Writes edited chunks out every `chunk-stream.edit-flush-seconds`. Touches no component, only [ChunkService]. */
@Component
class ChunkEditPersistSystem(
  private val journal: ChunkEditJournal,
  settings: ChunkStreamConfig,
) : System {
  override val phase = Phase.PERSIST

  override val schedule: Schedule = Schedule.EverySeconds(settings.editFlushSeconds)

  override val reads: ComponentClassSet = emptySet()

  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    journal.flushDirty()
  }
}
