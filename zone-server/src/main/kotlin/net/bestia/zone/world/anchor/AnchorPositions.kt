package net.bestia.zone.world.anchor

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position

/** Where the world is simulated as if a player stood there: what dens and wildlife wake around. */
object AnchorPositions {

  fun of(world: World): List<Vec3L> {
    val positions = ArrayList<Vec3L>()
    world.query(Position::class, SimulationAnchor::class).each {
      positions.add(get<Position>().toVec3L())
    }
    return positions
  }
}
