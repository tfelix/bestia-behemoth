package net.bestia.zone.spawn.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * Takes a cut of kill EXP before the player gets it, such as a guild's tax. A port: the slices that levy sit
 * above this one.
 *
 * Called on the tick, from the death system's deferred EXP block. It must only read the world and hand work
 * off: no database, no blocking.
 */
interface KillExpLevy {
  /** What [levy] reads, so the death system can declare it. */
  val reads: ComponentClassSet

  /** @return how much of [amount] the player behind [masterEntityId] keeps */
  fun levy(world: World, masterEntityId: EntityId, amount: Int): Int
}
