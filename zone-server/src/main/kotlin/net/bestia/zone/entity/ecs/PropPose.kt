package net.bestia.zone.entity.ecs

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.geometry.Vec3L

/**
 * Where a static entity stands, and which way it faces.
 *
 * ### Why this is not [net.bestia.zone.movement.ecs.Position]
 *
 * Being out of the `Position` store spares a resident population of tens of thousands of things that never
 * move from several per-tick costs: `ChunkStreamSystem.groundNewcomers` scans the whole `Position` store every
 * tick looking for ungrounded entities, `MoveSystem` queries it, and a fresh `Position` is reported to the sync
 * and the area-of-interest index the moment it is added.
 *
 * A static entity **is** in the interest octree - an area-of-effect spell has to find a tree - but it is put
 * there directly by the residency service rather than through the dirty-position path. Those two things looked
 * coupled and are not.
 *
 * Promoted on interaction: the moment something targets or damages one, `Position` and `Health` are added and
 * it becomes an ordinary entity for as long as that lasts.
 */
data class PropPose(
  val position: Vec3L,

  /** Radians, so a client can turn a tree rather than planting a forest of clones all facing the same way. */
  val yaw: Float
) : Component
