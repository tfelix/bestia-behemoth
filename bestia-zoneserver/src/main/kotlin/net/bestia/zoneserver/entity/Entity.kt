package net.bestia.zoneserver.entity

import mu.KotlinLogging
import net.bestia.zoneserver.entity.component.Component
import java.io.Serializable

private val LOG = KotlinLogging.logger { }

/**
 * Entities can be attached with components in order to make a composition of a
 * complex entity which can be interacted with inside the bestia system.
 *
 * @author Thomas Felix
 */
class Entity(
        /**
         * Returns the unique ID for each entity.
         *
         * @return The unique ID for each entity.
         */
        val id: Long
) : Serializable {

  private val components = mutableMapOf<String, Long>()

  /**
   * Adds a given component reference to this entity. Note that the component
   * must be saved independently in the system, only the reference to it is
   * stored inside the entity.
   *
   * @param comp
   * The component to be added.
   */
  internal fun addComponent(comp: Component) {
    val simpleName = comp.javaClass.simpleName
    LOG.trace("Adding component {} (id: {}) to entity id: {}.", simpleName, comp.id, id)
    components[simpleName] = comp.id
  }

  /**
   * Removes a component from the entity.
   *
   * @param comp
   * The component to be removed.
   */
  internal fun removeComponent(comp: Component) {
    val simpleName = comp.javaClass.simpleName
    LOG.trace("Removing component {} from entity id: {}.", simpleName, id)
    components.remove(simpleName)
  }

  /**
   * Removes the component via its id from the entity.
   *
   * @param compId
   * The ID of the component to be removed.
   */
  internal fun removeComponent(compId: Long) {
    LOG.trace("Removing component id {} from entity: {}.", compId, id)
    components.entries.asSequence()
            .filter { (_, id) -> id == compId }
            .map { (name, _) -> name }
            .firstOrNull()?.let { components.remove(it) }
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
    return components[clazz.simpleName] ?: 0
  }
}
