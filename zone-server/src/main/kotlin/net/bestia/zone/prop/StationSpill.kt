package net.bestia.zone.prop

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L

/** Empties a station that is gone onto the ground. The crafting slice implements it, as it fills stations. */
interface StationSpill {

  fun spill(world: World, station: StructureEntry, at: Vec3L)
}
