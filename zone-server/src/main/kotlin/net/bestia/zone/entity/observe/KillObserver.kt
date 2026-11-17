package net.bestia.zone.entity.observe

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World

/**
 * Hears every kill that pays EXP. The death code states facts and knows nothing of who listens, so quests today
 * and statistics or achievements later hook in without touching it. Runs on the tick, in the death system's slot.
 */
interface KillObserver {

  /** What [onKill] reads and writes, so the death system can declare it. */
  val reads: ComponentClassSet
  val writes: ComponentClassSet

  fun onKill(world: World, kill: KillFact)
}
