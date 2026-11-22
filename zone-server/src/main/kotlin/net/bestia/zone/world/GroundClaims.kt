package net.bestia.zone.world

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/**
 * Ground that holds against players, such as an Empyrite Seed's core or a Stronghold's grounds. A higher slice claims
 * it; whatever digs or builds asks every claim first.
 */
interface GroundClaims {

  /** Why a player may not dig [radius] voxels around [at], or null. */
  fun digRefusal(at: Vec3L, radius: Double): OpError?

  /** Why [builderId] may not put up a structure at [at], or null. */
  fun buildRefusal(world: World, builderId: EntityId, at: Vec3L): OpError?
}

fun List<GroundClaims>.digRefusal(at: Vec3L, radius: Double): OpError? {
  return firstNotNullOfOrNull { it.digRefusal(at, radius) }
}

fun List<GroundClaims>.buildRefusal(world: World, builderId: EntityId, at: Vec3L): OpError? {
  return firstNotNullOfOrNull { it.buildRefusal(world, builderId, at) }
}
