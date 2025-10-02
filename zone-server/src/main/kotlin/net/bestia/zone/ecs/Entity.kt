package net.bestia.zone.ecs

import kotlin.reflect.KClass

data class Entity(
  val id: Long
) {

  private val components: MutableMap<KClass<out Component>, Component> = mutableMapOf()

  fun <T : Component> get(componentClass: KClass<T>): T? {
    @Suppress("UNCHECKED_CAST")
    return components[componentClass] as? T
  }

  fun <T : Component> getOrDefault(componentClass: KClass<T>, fn: () -> T): T {
    @Suppress("UNCHECKED_CAST")
    return components[componentClass] as? T
      ?: fn().also { add(it) }
  }

  fun <T : Component> getOrThrow(componentClass: KClass<T>): T {
    @Suppress("UNCHECKED_CAST")
    return components[componentClass] as? T
      ?: throw ComponentNotFoundException(componentClass.java)
  }

  fun <T : Component> add(component: T) {
    components[component::class] = component
  }

  fun <T : Component> addAll(vararg components: T) {
    components.forEach { add(it) }
  }

  fun <T : Component> remove(componentClass: KClass<T>): T? {
    @Suppress("UNCHECKED_CAST")
    return components.remove(componentClass) as? T
  }

  fun has(componentClass: KClass<out Component>): Boolean {
    return components.containsKey(componentClass)
  }
}