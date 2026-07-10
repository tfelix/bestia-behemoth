package net.bestia.zone.ecs.core

import kotlin.reflect.KClass

/**
 * Optional descriptor used to enable object pooling for a component type.
 *
 */
class ComponentType<T : Component>(
  val type: KClass<T>,
  val factory: () -> T,
  val reset: (T) -> Unit = {},
)
