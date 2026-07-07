package net.bestia.zone.ecs.movement

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs2.Component
import net.bestia.zone.ecs2.Ecs2System
import net.bestia.zone.ecs2.World
import org.springframework.core.annotation.Order
import kotlin.reflect.KClass
import org.springframework.stereotype.Component as SpringComponent

@SpringComponent
@Order(40)
class MoveSystem : Ecs2System {

  override val reads: Set<KClass<out Component>> = setOf(Speed::class)
  override val writes: Set<KClass<out Component>> = setOf(Position::class, Path::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Position::class, Speed::class, Path::class).each { id, position, speed, movementPath ->
      // calculate the movement advances of the entity since the last call.
      position.fraction += speed.speed * deltaTime

      // entity has moved more than one tile so its position can be updated.
      while (position.fraction > 1) {
        val nextPoint = movementPath.removeFirst()
        position.x = nextPoint.x
        position.y = nextPoint.y
        position.z = nextPoint.z

        world.markChanged<Position>(id)

        LOG.trace { "Entity $id on $nextPoint" }

        if (movementPath.path.isEmpty()) {
          world.remove<Path>(id)
        }

        position.fraction -= 1
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
