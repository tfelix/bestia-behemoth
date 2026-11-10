package net.bestia.zone.movement

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.navigation.local.LocalWalkQuery
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Moves an entity at once along a straight line, as a dash, a leap or a knockback does: tile by tile, so a wall
 * or a cliff stops it where a walker would stop. The move lands in the same tick, whatever the entity's speed.
 */
@Component
class ForcedMovement(private val walk: LocalWalkQuery) {

  /** Towards [towards], up to [maxTiles], stopping [keepDistance] tiles short. Returns the tiles moved. */
  fun towards(world: World, entityId: EntityId, towards: Vec3L, maxTiles: Int, keepDistance: Int = 0): Int {
    val position = world.get(entityId, Position::class) ?: return 0
    val from = position.toVec3L()
    val dx = towards.x - from.x
    val dy = towards.y - from.y
    val distance = max(abs(dx), abs(dy))
    val steps = min(maxTiles.toLong(), distance - keepDistance).toInt()

    var at = from
    var moved = 0
    for (step in 1..steps) {
      val column = Vec3L(from.x + dx * step / distance, from.y + dy * step / distance, at.z)
      val next = column.copy(z = walk.surfaceAt(column) ?: break)
      if (!walk.canStep(at, next)) break

      at = next
      moved = step
    }

    if (moved > 0) {
      position.x = at.x
      position.y = at.y
      position.z = at.z
      // Whatever it was walking is void where it now stands.
      world.remove(entityId, Path::class)
    }

    return moved
  }

  /**
   * Puts the entity on the ground at [to] at once, without walking the way there - a shadow step. False when
   * there is no ground to stand on at [to].
   */
  fun blinkTo(world: World, entityId: EntityId, to: Vec3L): Boolean {
    val position = world.get(entityId, Position::class) ?: return false
    val z = walk.surfaceAt(to) ?: return false

    position.x = to.x
    position.y = to.y
    position.z = z
    world.remove(entityId, Path::class)

    return true
  }

  /** Away from [from] by up to [tiles]. Standing on [from] gives no direction, so it does not move. */
  fun awayFrom(world: World, entityId: EntityId, from: Vec3L, tiles: Int): Int {
    val at = world.get(entityId, Position::class)?.toVec3L() ?: return 0
    val dx = (at.x - from.x).toDouble()
    val dy = (at.y - from.y).toDouble()
    val length = sqrt(dx * dx + dy * dy)
    if (length < 1e-6) return 0

    val target = Vec3L(at.x + (dx / length * tiles).roundToLong(), at.y + (dy / length * tiles).roundToLong(), at.z)

    return towards(world, entityId, target, tiles)
  }
}
