package net.bestia.zone.ground

import net.bestia.zone.geometry.Vec3L

/** Whether somebody made the ground at a spot: a road, a paved street, a bridge, a built floor. */
fun interface MadeGround {

  /** Null while nobody can tell yet, like a column still being built. */
  fun isMadeAt(position: Vec3L): Boolean?
}
