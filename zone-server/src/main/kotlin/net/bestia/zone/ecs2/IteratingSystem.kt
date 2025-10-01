package net.bestia.zone.ecs2

import kotlin.reflect.KClass

// System interface for ECS systems
abstract class IteratingSystem(
  vararg val requiredComponents: KClass<out Component>
) {
  abstract fun update(deltaTime: Long, entity: Entity, zone: ZoneServer)

  // Method to check if an entity has all required components
  fun entityMatches(entity: Entity): Boolean {
    return requiredComponents.all { componentClass ->
      entity.has(componentClass)
    }
  }
}