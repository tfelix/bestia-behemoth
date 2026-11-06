package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * A port for a feature above the item tier that has a say when a ground item is picked up, like the post for a
 * fallen mail sack. Called by [ObtainItemIntentSystem] on the tick, while the ground item still exists.
 */
interface GroundPickupRule {
  val reads: ComponentClassSet
  val writes: ComponentClassSet

  /** False keeps the item on the ground. */
  fun mayPickUp(world: World, looter: EntityId, stack: EntityId): Boolean

  /** The pickup is certain: the item leaves the ground for the looter's bag right after this. */
  fun onPickUp(world: World, looter: EntityId, stack: EntityId)
}
