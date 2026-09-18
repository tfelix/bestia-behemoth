package net.bestia.zone.ecs.movement

/**
 * Told when something sets foot on a tile.
 *
 * A seam for `GroundHeight`'s reason, and the same shape: the real answer needs a generated world and a live
 * wear registry, and a test about walking should not have to build either. The no-op implementation is what
 * every movement test gets.
 */
fun interface GroundTrample {

  fun steppedOn(voxelX: Long, voxelY: Long)

  companion object {

    /** For tests, and for any build where nothing is recording what the ground has been through. */
    val NONE = GroundTrample { _, _ -> }
  }
}
