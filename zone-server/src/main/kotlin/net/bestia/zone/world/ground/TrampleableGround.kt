package net.bestia.zone.world.ground

/**
 * How readily the ground at one tile wears down under traffic.
 *
 * An interface with one real implementation, for the reason `BurnableGround` and `ForageGround` share: the
 * real answer needs a generated world, and a test asking "does a road stay unworn" should not have to build
 * one to find out.
 */
fun interface TrampleableGround {

  /** `0` for ground that never shows a path, up to `1` for ground that wears bare quickly. */
  fun wearAt(voxelX: Long, voxelY: Long): Double
}
