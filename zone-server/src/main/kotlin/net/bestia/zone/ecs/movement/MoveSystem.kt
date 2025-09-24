package net.bestia.zone.ecs.movement

import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.IteratingSystem
import com.github.quillraven.fleks.World.Companion.family
import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.network.IsDirty

class MoveSystem : IteratingSystem(
  family { all(Position, Speed, Path) }
) {
  override fun onTickEntity(entity: Entity) {
    // calculate the movement advances of the entity since the last call.
    val speed = entity[Speed]
    val d = speed.speed * deltaTime

    val position = entity[Position]
    position.fraction += d

    // entity has moved more than one tile so its position can be updated.
    while (position.fraction > 1) {
      val movementPath = entity[Path]

      val nextPoint = movementPath.removeFirst()
      position.x = nextPoint.x
      position.y = nextPoint.y
      position.z = nextPoint.z

      entity.configure {
        entity += IsDirty
      }

      LOG.trace { "Entity ${entity.id} on $nextPoint" }

      if (movementPath.path.isEmpty()) {
        entity.configure {
          entity -= Path
        }
        position.fraction = 0f
      }

      position.fraction -= 1
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
