package net.bestia.zone.ecs.core

import org.reflections.Reflections
import org.reflections.scanners.Scanners
import kotlin.reflect.KClass

/**
 * Every concrete [Component] that also implements [Dirtyable] - the "syncable" component types
 * [net.bestia.zone.ecs.ZoneEngine] flushes to clients whenever they're dirty, and that
 * [net.bestia.zone.ecs.visibility.EntitySnapshotBuilder] sends in full when an entity comes into view.
 *
 * Held here rather than scanned per consumer: [scanDirtyableComponentTypes] walks the classpath, which is
 * worth a few hundred milliseconds of boot once and not twice.
 */
val dirtyableComponentTypes: List<KClass<out Component>> by lazy { scanDirtyableComponentTypes() }

/**
 * Scans the classpath for every concrete [Component] that also implements [Dirtyable]. Prefer
 * [dirtyableComponentTypes], which does this once.
 *
 * Ordered by name, because `Reflections` answers with a `HashSet` and the flush sends one entity's updates
 * for a tick in whatever order this list is in. An order that can differ between JVM runs makes the wire
 * order of two components of the same entity unreproducible - and the client reconciles a path against the
 * position it arrives with, so which of the two comes first is not a detail.
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
    .sortedBy { it.qualifiedName }
    .toList()
}
