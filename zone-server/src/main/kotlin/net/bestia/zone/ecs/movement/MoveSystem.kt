package net.bestia.zone.ecs.movement

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

@SpringComponent
@Order(40)
class MoveSystem : System {

  override val reads: ComponentClassSet = setOf(Speed::class)
  override val writes: ComponentClassSet = setOf(Position::class, Path::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Position::class, Speed::class, Path::class).each { id ->
      val position = get<Position>()
      val speed = get<Speed>()
      val movementPath = get<Path>()

      // calculate the movement advances of the entity since the last call.
      position.fraction += speed.speed * deltaTime

      // entity has moved more than one tile so its position can be updated.
      while (position.fraction > 1) {
        val nextPoint = movementPath.removeFirst()
        position.x = nextPoint.x
        position.y = nextPoint.y
        position.z = nextPoint.z

        LOG.trace { "Entity $id on $nextPoint" }

        if (movementPath.path.isEmpty()) {
          world.remove(id, Path::class)
        }

        position.fraction -= 1
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
