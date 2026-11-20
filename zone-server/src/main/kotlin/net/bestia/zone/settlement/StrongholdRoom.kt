package net.bestia.zone.settlement

import net.bestia.zone.geometry.Vec3L

/** The room a Guild Stronghold keeps free of towns. Answered by the `stronghold` slice, which sits above this one. */
fun interface StrongholdRoom {

  /** Whether a city sign at [at] would stand too close to an awake Empyrite Seed. */
  fun isTooCloseToSeed(at: Vec3L): Boolean
}
