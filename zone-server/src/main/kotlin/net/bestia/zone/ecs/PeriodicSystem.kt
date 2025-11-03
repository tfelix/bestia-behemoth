package net.bestia.zone.ecs

import kotlin.reflect.KClass

/**
 * System interface for ECS systems that run periodically based on a delay
 */
abstract class PeriodicSystem(
  private val delay: Float,
  val requiredComponents: Set<KClass<out Component>>,
) {

  private var delayAccumulator = 0f

  abstract fun update(deltaTime: Float, entity: Entity, zone: ZoneServer)

  // Method to check if an entity has all required components
  fun entityMatches(entity: Entity): Boolean {
    return requiredComponents.all { componentClass ->
      entity.has(componentClass)
    }
  }

  // Check if enough time has passed since last execution
  fun shouldExecute(deltaTime: Float): Boolean {
    delayAccumulator += deltaTime

    if (delayAccumulator >= delay) {
      delayAccumulator = 0f
      return true
    }
    return false
  }
}
