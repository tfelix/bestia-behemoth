package net.bestia.zone.ecs.core

import kotlin.reflect.KClass

/**
 * Optional descriptor used to enable object pooling for a component type.
 *
 * When registered via [World.registerPooled], removed instances are recycled
 * (after [reset]) and handed back out by [ComponentStore.obtain] instead of
 * allocating, which keeps the JVM GC quiet on hot spawn/despawn paths.
 */
class ComponentType<T : Component>(
  val type: KClass<T>,
  val factory: () -> T,
  val reset: (T) -> Unit = {},
)
