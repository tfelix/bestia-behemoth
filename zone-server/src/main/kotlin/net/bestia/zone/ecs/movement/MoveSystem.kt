package net.bestia.zone.ecs.movement

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.network.IsDirty
import net.bestia.zone.ecs2.Entity
import net.bestia.zone.ecs2.IteratingSystem
import net.bestia.zone.ecs2.ZoneServer
import org.springframework.stereotype.Component

@Component
class MoveSystem : IteratingSystem() {

  override val requiredComponents: Set<kotlin.reflect.KClass<out net.bestia.zone.ecs2.Component>> = setOf(
    Position::class,
    Speed::class,
    Path::class
  )

  override fun update(
    deltaTime: Float,
    entity: Entity,
    zone: ZoneServer
  ) {
    // calculate the movement advances of the entity since the last call.
    val speed = entity.getOrThrow(Speed::class)
    val d = speed.speed * deltaTime

    val position = entity.getOrThrow(Position::class)
    position.fraction += d

    // entity has moved more than one tile so its position can be updated.
    while (position.fraction > 1) {
      val movementPath = entity.getOrThrow(Path::class)

      val nextPoint = movementPath.removeFirst()
      position.x = nextPoint.x
      position.y = nextPoint.y
      position.z = nextPoint.z

      entity.add(IsDirty)

      LOG.trace { "Entity ${entity.id} on $nextPoint" }

      if (movementPath.path.isEmpty()) {
        entity.remove(Path::class)

        position.fraction = 0f
      }

      position.fraction -= 1
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
