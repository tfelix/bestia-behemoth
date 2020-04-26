package net.bestia.zoneserver.entity

import mu.KotlinLogging
import net.bestia.zoneserver.entity.component.Component
import java.io.Serializable
import java.lang.NullPointerException

private val LOG = KotlinLogging.logger { }
typealias EntityId = Long

/**
 * Entities can be attached with components in order to make a composition of a
 * complex entity which can be interacted with inside the Bestia system.
 *
 * @author Thomas Felix
 */
data class Entity(
    /**
     * @return The unique ID for each entity.
     */
    val id: EntityId
) : Serializable {

  private val components = mutableMapOf<Class<out Component>, Component>()

  val allComponents get() = components.values.toSet()

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

  internal fun addAllComponents(comps: Collection<Component>) {
    comps.forEach { addComponent(it) }
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

  override fun toString(): String {
    return "Entity[id: $id, components: [${components.map { it.key.simpleName.toUpperCase() }.joinToString(", ")}]]"
  }

  companion object {
    fun withComponents(entityId: Long, components: Collection<Component>): Entity {
      return Entity(entityId).apply {
        this.components.putAll(components.map { it::class.java to it }.toMap())
      }
    }
  }
}
