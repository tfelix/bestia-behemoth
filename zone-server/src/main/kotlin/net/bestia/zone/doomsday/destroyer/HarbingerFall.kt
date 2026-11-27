package net.bestia.zone.doomsday.destroyer

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.entity.observe.KillObserver
import org.springframework.stereotype.Component

/** Notices when the players bring the Harbinger down. Heard on the tick and acted on by [Harbinger] off it. */
@Component
class HarbingerFall : KillObserver {

  override val reads: ComponentClassSet = emptySet()
  override val writes: ComponentClassSet = emptySet()

  @Volatile
  private var fallen = false

  override fun onKill(world: World, kill: KillFact) {
    if (kill.event?.event == EVENT) {
      fallen = true
    }
  }

  /** Whether it fell since the last ask. */
  fun take(): Boolean {
    val fell = fallen
    fallen = false
    return fell
  }

  companion object {
    const val EVENT = "Harbinger"
  }
}
