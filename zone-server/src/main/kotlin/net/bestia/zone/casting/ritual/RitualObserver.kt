package net.bestia.zone.casting.ritual

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/** Hears every ritual that ran its full time, with where its circle stood and who stayed to the end. On the tick. */
fun interface RitualObserver {

  fun onRitualCompleted(world: World, at: Vec3L, participants: List<EntityId>)
}
