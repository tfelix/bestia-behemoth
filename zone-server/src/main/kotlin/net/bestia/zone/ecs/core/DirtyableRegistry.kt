package net.bestia.zone.ecs.core

import net.bestia.zone.ecs.Dirtyable
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.springframework.stereotype.Service
import kotlin.reflect.KClass

/** Discovers every [Dirtyable] [Component] on the classpath once, replacing a hand-maintained list. */
@Service
class DirtyableRegistry {
  val syncTypes: List<KClass<out Component>> by lazy {
    Reflections("net.bestia.zone", Scanners.SubTypes)
      .getSubTypesOf(Dirtyable::class.java)
      .asSequence()
      .filter { Component::class.java.isAssignableFrom(it) && !it.isInterface }
      .map {
        @Suppress("UNCHECKED_CAST")
        it.kotlin as KClass<out Component>
      }
      .toList()
  }
}
