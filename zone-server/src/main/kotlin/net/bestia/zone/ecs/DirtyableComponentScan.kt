package net.bestia.zone.ecs

import net.bestia.zone.ecs.core.Component
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import kotlin.reflect.KClass

/**
 * Classpath-scans [basePackage] for concrete [Component] classes that also implement [Dirtyable],
 * i.e. every component type [ZoneEngine] must check for outbound sync after each tick. Extracted
 * out of [ZoneEngine] so the discovery logic can be exercised on its own.
 */
fun scanDirtyableComponentTypes(basePackage: String = "net.bestia.zone"): List<KClass<out Component>> {
  return Reflections(basePackage, Scanners.SubTypes)
    .getSubTypesOf(Dirtyable::class.java)
    .asSequence()
    .filter { Component::class.java.isAssignableFrom(it) && !it.isInterface }
    .map {
      @Suppress("UNCHECKED_CAST")
      it.kotlin as KClass<out Component>
    }
    .toList()
}
