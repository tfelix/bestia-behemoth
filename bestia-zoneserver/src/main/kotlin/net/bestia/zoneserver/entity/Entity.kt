package net.bestia.zoneserver.entity

import mu.KotlinLogging
import net.bestia.zoneserver.entity.component.Component
import java.io.Serializable
import java.lang.NullPointerException

private val LOG = KotlinLogging.logger { }

/**
 * Entities can be attached with components in order to make a composition of a
 * complex entity which can be interacted with inside the bestia system.
 *
 * @author Thomas Felix
 */
class Entity(
    /**
     * @return The unique ID for each entity.
     */
    val id: Long
) : Serializable {

  private val components = mutableMapOf<Class<out Component>, Component>()

  /**
   * Adds a given component reference to this entity. Note that the component
   * must be saved independently in the system, only the reference to it is
   * stored inside the entity.
   *
   * @param comp
   * The component to be added.
   */
  internal fun addComponent(comp: Component) {
    LOG.trace { "Adding component $comp to entity id:  $id." }
    components[comp.javaClass] = comp
  }

  /**
   * Removes a component from the entity.
   *
   * @param comp
   * The component to be removed.
   */
  internal fun removeComponent(comp: Component) {
    LOG.trace { "Removing component $comp from entity id: $id." }
    components.remove(comp.javaClass)
  }

  /**
   * Returns the associated component ID with this component class.
   *
   * @param clazz
   * The class of the component.
   * @return The ID is this component is attached to this entity or 0
   * otherwise.
   */
  internal fun getComponentId(clazz: Class<out Component>): Long {
    return components[clazz]?.id ?: 0
  }

  internal fun <T : Component> getComponent(clazz: Class<T>): T {
    return tryGetComponent(clazz) ?: throw NullPointerException("Entity $id has no component of type $clazz")
  }

  internal fun <T : Component> tryGetComponent(clazz: Class<T>): T? {
    @Suppress("UNCHECKED_CAST")
    return components[clazz] as? T
  }

  fun hasComponent(compClass: Class<out Component>): Boolean {
    return components.containsKey(compClass)
  }

  companion object {
    fun withComponents(entityId: Long, components: Collection<Component>): Entity {
      return Entity(entityId).apply {
        this.components.putAll(components.map { it::class.java to it }.toMap())
      }
    }
  }
}
