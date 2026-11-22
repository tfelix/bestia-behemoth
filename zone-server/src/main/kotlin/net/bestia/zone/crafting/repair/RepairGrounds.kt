package net.bestia.zone.crafting.repair

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/** Ground outside a player town where a building may be repaired too, such as a Stronghold's grounds for its guild. */
fun interface RepairGrounds {
  fun mayRepairAt(world: World, repairerId: EntityId, at: Vec3L): Boolean
}
