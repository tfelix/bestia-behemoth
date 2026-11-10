package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/** Whether something still fits in a bag: the one rule a pickup, a transfer and a finished craft obey. */
object CarryCheck {

  /**
   * True if [entityId] has an inventory at all and adding [weight] would still fit its carry capacity.
   *
   * Weighed against the live [Inventory] rather than [CarryCapacity.current]: two grants in one tick would both
   * pass a tick-old `current`, and `CurMax` clamps `current` to `max`, so an entity over its limit would
   * under-report its load. `Inventory.totalWeight` cannot lie about either.
   */
  fun fits(world: World, entityId: EntityId, weight: Int): Boolean {
    val inventory = world.get(entityId, Inventory::class) ?: return false
    val capacity = world.get(entityId, CarryCapacity::class) ?: return false

    return inventory.totalWeight + weight <= capacity.max
  }
}
