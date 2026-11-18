package net.bestia.zone.entity.observe

import net.bestia.zone.ecs.core.World

/**
 * Hears what players do to the things and people of the world, as [KillObserver] hears kills. The code that
 * handles a click states the fact and knows nothing of who listens. Runs on the tick.
 */
interface DeedObserver {

  fun onDeed(world: World, deed: DeedFact)
}
