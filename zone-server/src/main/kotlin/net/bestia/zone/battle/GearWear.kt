package net.bestia.zone.battle

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Lets a fight wear down or strip what an entity wears, without battle reaching up into the item slice. */
interface GearWear {

  /** Takes [durability] points off the weapon in hand, breaking it at zero. */
  fun wearWeapon(world: World, entityId: EntityId, durability: Int)

  companion object {
    val NONE = object : GearWear {
      override fun wearWeapon(world: World, entityId: EntityId, durability: Int) {}
    }
  }
}
