package net.bestia.zone.ecs.core

import org.reflections.Reflections
import org.reflections.scanners.Scanners
import kotlin.reflect.KClass

/**
 * Scans the classpath for every concrete [Component] that also implements [Dirtyable] - the
 * "syncable" component types [net.bestia.zone.ecs.ZoneEngine] flushes to clients whenever they're dirty.
 */
fun scanDirtyableComponentTypes(): List<KClass<out Component>> {
  return Reflections("net.bestia.zone", Scanners.SubTypes)
    .getSubTypesOf(Dirtyable::class.java)
    .asSequence()
    .filter { Component::class.java.isAssignableFrom(it) && !it.isInterface }
    .map {
      @Suppress("UNCHECKED_CAST")
      it.kotlin as KClass<out Component>
    }
    .toList()
}
