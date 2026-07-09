package net.bestia.zone.ai.behavior

import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.core.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import kotlin.random.Random

/**
 * Small locomotion helper shared by the movement leaves. Movement is currently a direct greedy
 * step onto an adjacent tile (as the old TestAiSystem did) because [net.bestia.zone.navigation]
 * pathfinding is still a stub. The leaves only ever call through here, so switching to real A*
 * pathfinding later is a change to this one file, not to the behaviour trees.
 */
object Locomotion {

  private val DIRECTIONS = listOf(
    Vec3L(-1, -1, 0), Vec3L(0, -1, 0), Vec3L(1, -1, 0),
    Vec3L(-1, 0, 0), Vec3L(1, 0, 0),
    Vec3L(-1, 1, 0), Vec3L(0, 1, 0), Vec3L(1, 1, 0)
  )

  fun position(world: World, entityId: EntityId): Vec3L = world.getOrThrow(entityId, Position::class).toVec3L()

  fun distanceTo(world: World, entityId: EntityId, target: Vec3L): Long = position(world, entityId).distance(target)

  fun isMoving(world: World, entityId: EntityId): Boolean = world.has(entityId, Path::class)

  /** Step one tile toward [target]. No-op if already moving or already on the target tile. */
  fun stepToward(world: World, entityId: EntityId, target: Vec3L) {
    if (isMoving(world, entityId)) {
      return
    }
    val cur = position(world, entityId)
    val next = cur + stepDelta(target.x - cur.x, target.y - cur.y)
    if (next != cur) {
      world.add(entityId, Path(mutableListOf(next)))
    }
  }

  /** Step one tile directly away from [threat]. No-op if already moving. */
  fun stepAwayFrom(world: World, entityId: EntityId, threat: Vec3L) {
    if (isMoving(world, entityId)) {
      return
    }
    val cur = position(world, entityId)
    val next = cur + stepDelta(cur.x - threat.x, cur.y - threat.y)
    if (next != cur) {
      world.add(entityId, Path(mutableListOf(next)))
    }
  }

  /**
   * Set a random short wander path, staying within [radius] tiles of [home] on both axes. No-op if
   * already moving or if every adjacent tile would leave that box (shouldn't happen for radius >= 1).
   */
  fun wanderStep(world: World, entityId: EntityId, home: Vec3L, radius: Long = 5) {
    if (isMoving(world, entityId)) {
      return
    }
    val cur = position(world, entityId)
    val candidates = DIRECTIONS
      .map { cur + it }
      .filter { (it.x - home.x) in -radius..radius && (it.y - home.y) in -radius..radius }
    val next = candidates.randomOrNull(Random.Default) ?: return
    world.add(entityId, Path(mutableListOf(next)))
  }

  private fun stepDelta(dx: Long, dy: Long): Vec3L =
    Vec3L(dx.coerceIn(-1, 1), dy.coerceIn(-1, 1), 0)
}
