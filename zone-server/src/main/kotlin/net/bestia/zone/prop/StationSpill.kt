package net.bestia.zone.prop

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L

/** Empties a structure that is gone onto the ground. Every slice that fills one implements it. */
interface StationSpill {

  fun spill(world: World, station: StructureEntry, at: Vec3L)
}
