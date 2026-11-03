package net.bestia.zone.economy

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** What a trader's standing with a town does to their prices. The honor slice, which sits above, implements it. */
interface TraderStanding {

  /** What [priceFor] reads, for a system that asks it from inside the tick. */
  val reads: ComponentClassSet

  /** What [trader] pays and is paid at [merchant]'s counter in [settlement], or null when the merchant refuses. */
  fun priceFor(world: World, trader: EntityId, settlement: Int, merchant: EntityId): PersonalPrice?
}
