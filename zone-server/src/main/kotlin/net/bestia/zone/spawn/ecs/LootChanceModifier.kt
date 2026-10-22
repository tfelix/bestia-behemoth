package net.bestia.zone.spawn.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.AccountId

/**
 * Raises the drop chances of a kill, such as a guild skill. A port: the slices that raise them sit above this
 * one. Called on the tick from the death system, so it must only read the world.
 */
interface LootChanceModifier {
  /** What [factor] reads, so the death system can declare it. */
  val reads: ComponentClassSet

  /** @return what every drop chance of a kill by [killerAccountId] is multiplied by */
  fun factor(world: World, killerAccountId: AccountId): Double
}
