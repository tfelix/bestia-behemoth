package net.bestia.zone.doomsday

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.entity.observe.KillObserver
import org.springframework.stereotype.Component

/**
 * Notices when the players bring down the Avatar of the Last Stand. Heard on the tick and acted on by [WorldEnd] off
 * it, because winning the Last Stand writes to the database.
 */
@Component
class DoomAvatarFall : KillObserver {

  override val reads: ComponentClassSet = emptySet()
  override val writes: ComponentClassSet = emptySet()

  @Volatile
  private var fallen = false

  override fun onKill(world: World, kill: KillFact) {
    if (kill.event?.event == EVENT) {
      fallen = true
    }
  }

  /** Whether the Avatar fell since the last ask. */
  fun take(): Boolean {
    val fell = fallen
    fallen = false
    return fell
  }

  companion object {
    /** The tag the Avatar of every threat carries. */
    const val EVENT = "DoomAvatar"
  }
}
