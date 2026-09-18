package net.bestia.zone.ecs.movement

import net.bestia.zone.util.EntityId

/**
 * Told when something sets foot on a tile, and where it came from.
 *
 * A seam for `GroundHeight`'s reason, and the same shape: the real answer needs a generated world and a live
 * mark registry, and a test about walking should not have to build either. The no-op implementation is what
 * every movement test gets.
 *
 * ### It carries the step, not the position
 *
 * The tile left as well as the tile entered, because a print has a heading and a heading is the difference
 * between the two. The entity comes along for the same reason: two creatures crossing the same ground leave
 * two sets of tracks, not one set twice.
 */
fun interface GroundTrample {

  fun steppedOn(entityId: EntityId, fromX: Long, fromY: Long, toX: Long, toY: Long)

  companion object {

    /** For tests, and for any build where nothing is recording what the ground has been through. */
    val NONE = GroundTrample { _, _, _, _, _ -> }
  }
}
