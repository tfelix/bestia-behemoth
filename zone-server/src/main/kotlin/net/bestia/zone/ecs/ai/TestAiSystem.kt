package net.bestia.zone.ecs.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.visual.BestiaVisual
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.ecs2.Entity
import net.bestia.zone.ecs2.IteratingSystem
import net.bestia.zone.ecs2.ZoneServer
import kotlin.random.Random

class TestAiSystem : IteratingSystem(
  Position::class,
  Speed::class,
  BestiaVisual::class
) {
  override fun update(
    deltaTime: Long,
    entity: Entity,
    zone: ZoneServer
  ) {
    if (entity.has(Path::class)) {
      return
    }
    var curPos = entity.getOrThrow(Position::class).toVec3L()
    val newPath = mutableListOf(curPos)
    while (newPath.size < 8) {
      val nextPos = getNextPosition(curPos)
      if (!isValidPosition(nextPos)) {
        continue
      } else {
        newPath.add(nextPos)
        curPos = nextPos
      }
    }
    entity.add(Path(newPath))
    LOG.debug { "Entity ${entity.id} moving now: $newPath" }

    LOG.debug { "Entity $entity moving now: $newPath" }
  }

  private fun getNextPosition(cur: Vec3L): Vec3L {
    // Define all possible movement directions (8 adjacent fields including diagonals)
    val directions = listOf(
      Vec3L(-1, -1, 0), // NW
      Vec3L(0, -1, 0),  // N
      Vec3L(1, -1, 0),  // NE
      Vec3L(-1, 0, 0),  // W
      Vec3L(1, 0, 0),   // E
      Vec3L(-1, 1, 0),  // SW
      Vec3L(0, 1, 0),   // S
      Vec3L(1, 1, 0)    // SE
    )

    val chosenDirection = directions.random(Random.Default)

    return cur + chosenDirection
  }

  private fun isValidPosition(pos: Vec3L): Boolean {
    return pos.x > -10 && pos.x < 10 && pos.y > -10 && pos.y < 10
  }


  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}