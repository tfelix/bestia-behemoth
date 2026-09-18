package net.bestia.zone.ecs.battle.damage

/**
 * Told where something died.
 *
 * A seam for `GroundTrample`'s reason, and the same shape: the real answer needs a generated world and a live
 * mark store, and a test about dying should not have to build either. The no-op implementation is what every
 * battle test gets.
 */
fun interface GroundSpill {

  fun bledAt(voxelX: Long, voxelY: Long)

  companion object {

    /** For tests, and for any build where nothing is recording what the ground has been through. */
    val NONE = GroundSpill { _, _ -> }
  }
}
