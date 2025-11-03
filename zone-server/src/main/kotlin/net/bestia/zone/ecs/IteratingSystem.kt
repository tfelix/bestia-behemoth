package net.bestia.zone.ecs

import kotlin.reflect.KClass

typealias ComponentSet = Set<KClass<out Component>>

// System interface for ECS systems
abstract class IteratingSystem {

  abstract val requiredComponents: Set<KClass<out Component>>

  abstract fun update(deltaTime: Float, entity: Entity, zone: ZoneServer)

  // Method to check if an entity has all required components
  fun entityMatches(entity: Entity): Boolean {
    return requiredComponents.all { componentClass ->
      entity.has(componentClass)
    }
  }
}