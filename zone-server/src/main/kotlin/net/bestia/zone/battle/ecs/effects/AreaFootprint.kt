package net.bestia.zone.battle.ecs.effects

/**
 * The ground columns an [AreaEffect] really covers, for an effect whose shape is not its cube.
 *
 * The cube still decides which entities are asked; this only removes the ones standing outside the shape.
 */
fun interface AreaFootprint {
  fun covers(voxelX: Long, voxelY: Long): Boolean
}
