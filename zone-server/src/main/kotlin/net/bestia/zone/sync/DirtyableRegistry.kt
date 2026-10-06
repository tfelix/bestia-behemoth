package net.bestia.zone.sync

import org.reflections.Reflections
import org.reflections.scanners.Scanners
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import net.bestia.zone.ecs.core.Component

/**
 * Every concrete [Component] that also implements [Dirtyable] - the "syncable" component types
 * [net.bestia.zone.engine.ZoneEngine] flushes to clients whenever they're dirty, and that
 * [net.bestia.zone.engine.EntitySnapshotBuilder] sends in full when an entity comes into view.
 *
 * Held here rather than scanned per consumer: [scanDirtyableComponentTypes] walks the classpath, which is
 * worth a few hundred milliseconds of boot once and not twice.
 */
val dirtyableComponentTypes: List<KClass<out Component>> by lazy { scanDirtyableComponentTypes() }

/**
 * Scans the classpath for every concrete [Component] that also implements [Dirtyable]. Prefer
 * [dirtyableComponentTypes], which does this once. Sorted by name: the scan returns a hash set, whose order
 * would change whenever a class changes package.
 */
fun scanDirtyableComponentTypes(): List<KClass<out Component>> {
  return Reflections("net.bestia.zone", Scanners.SubTypes)
    .getSubTypesOf(Dirtyable::class.java)
    .asSequence()
    .filter { Component::class.java.isAssignableFrom(it) && !Modifier.isAbstract(it.modifiers) }
    .map {
      @Suppress("UNCHECKED_CAST")
      it.kotlin as KClass<out Component>
    }
    .sortedBy { it.java.simpleName }
    .toList()
}
