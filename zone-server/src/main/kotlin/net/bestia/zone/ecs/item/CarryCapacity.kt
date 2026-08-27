package net.bestia.zone.ecs.item

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.battle.status.CurMax

/**
 * Tracks carried inventory weight (current) against the weight limit derived from
 * Attributes/Level (max), both on [net.bestia.zone.item.Item.weight]'s scale of 100 per kilogram.
 *
 * Owned by [CarryCapacitySystem], which mirrors `current` off [Inventory.totalWeight] every tick and
 * recomputes `max` whenever the attributes or level behind it move.
 * [lastKnownStrength]/[lastKnownVitality]/[lastKnownLevel] are what let it skip the latter.
 *
 * Known limitation: [net.bestia.zone.battle.status.CurMax] clamps `current` into `0..max`, so an entity
 * genuinely over its limit reads as exactly full rather than overweight. Harmless while nothing can become
 * overweight - [ObtainItemIntentSystem] gates on the inventory itself, not on this - but the day
 * over-encumbrance carries a penalty this has to stop extending `CurMax`, which is shared with Health,
 * Mana and Stamina and should not learn about overflow on their account.
 */
class CarryCapacity(
  current: Int,
  max: Int,
) : CurMax(current, max), Component {

  var lastKnownStrength: Int = -1
  var lastKnownVitality: Int = -1
  var lastKnownLevel: Int = -1

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return CarryCapacityComponentSMSG(
      entityId = entityId,
      current = current,
      max = max
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.OwnerOnly
  }
}
