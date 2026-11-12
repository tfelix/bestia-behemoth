package net.bestia.zone.movement.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.geometry.Vec3L

/**
 * Which way an entity looks, as a direction on the ground: the way it last stepped, or towards what it last struck.
 * What "behind" a target means for a backstab, and which way "back the way it came" pushes.
 */
data class Facing(var dx: Int = 0, var dy: Int = 1) : Component {

  /** Turns towards [to] from [from]; standing on the same spot keeps the old facing. */
  fun turnTowards(from: Vec3L, to: Vec3L) {
    val x = (to.x - from.x).coerceIn(-1, 1).toInt()
    val y = (to.y - from.y).coerceIn(-1, 1).toInt()
    if (x == 0 && y == 0) return
    dx = x
    dy = y
  }

  /** The tile right behind an entity at [at] that faces this way. */
  fun behind(at: Vec3L): Vec3L {
    return Vec3L(at.x - dx, at.y - dy, at.z)
  }

  /** Whether [other] stands in the half behind an entity at [at] that faces this way. */
  fun isBehind(at: Vec3L, other: Vec3L): Boolean {
    return (other.x - at.x) * dx + (other.y - at.y) * dy < 0
  }
}
