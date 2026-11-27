package net.bestia.zone.doomsday.destroyer

import net.bestia.zone.doomsday.DoomAvatarFall
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.entity.observe.KillObserver
import org.springframework.stereotype.Component

/**
 * Notices when the players bring the Destroyer down, also as the Avatar of the Last Stand. Heard on the tick and acted
 * on by [DestroyerThreat] off it.
 */
@Component
class DestroyerFall : KillObserver {

  override val reads: ComponentClassSet = emptySet()
  override val writes: ComponentClassSet = emptySet()

  @Volatile
  private var fallen = false

  override fun onKill(world: World, kill: KillFact) {
    val event = kill.event ?: return
    if (event.event == EVENT || event.event == DoomAvatarFall.EVENT && event.subjectId == AVATAR_SUBJECT) {
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
    const val EVENT = "Destroyer"

    /** Tells the Destroyer apart from another threat's Avatar. */
    const val AVATAR_SUBJECT = 263L
  }
}
