package net.bestia.zone.ai.behavior

import net.bestia.zone.ecs.Entity
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
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

  fun position(entity: Entity): Vec3L = entity.getOrThrow(Position::class).toVec3L()

  fun distanceTo(entity: Entity, target: Vec3L): Long = position(entity).distance(target)

  fun isMoving(entity: Entity): Boolean = entity.has(Path::class)

  /** Step one tile toward [target]. No-op if already moving or already on the target tile. */
  fun stepToward(entity: Entity, target: Vec3L) {
    if (isMoving(entity)) {
      return
    }
    val cur = position(entity)
    val next = cur + stepDelta(target.x - cur.x, target.y - cur.y)
    if (next != cur) {
      entity.add(Path(mutableListOf(next)))
    }
  }

  /** Step one tile directly away from [threat]. No-op if already moving. */
  fun stepAwayFrom(entity: Entity, threat: Vec3L) {
    if (isMoving(entity)) {
      return
    }
    val cur = position(entity)
    val next = cur + stepDelta(cur.x - threat.x, cur.y - threat.y)
    if (next != cur) {
      entity.add(Path(mutableListOf(next)))
    }
  }

  /** Set a random short wander path. No-op if already moving. */
  fun wanderStep(entity: Entity) {
    if (isMoving(entity)) {
      return
    }
    val next = position(entity) + DIRECTIONS.random(Random.Default)
    entity.add(Path(mutableListOf(next)))
  }

  private fun stepDelta(dx: Long, dy: Long): Vec3L =
    Vec3L(dx.coerceIn(-1, 1), dy.coerceIn(-1, 1), 0)
}
