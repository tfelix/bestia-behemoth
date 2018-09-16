package net.bestia.zoneserver.entity

import mu.KotlinLogging
import net.bestia.entity.component.interceptor.Interceptor
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.entity.component.Component
import org.springframework.stereotype.Service
import java.lang.reflect.Constructor
import java.util.*
import java.util.stream.Collectors

private val LOG = KotlinLogging.logger { }

/**
 * The [EntityService] is a central very important part of the bestia
 * game. It gives access to all entities in the game which represent all
 * interactive beeings with which the player can interact.
 *
 * @author Thomas Felix
 */
@Service
class EntityService(
        private val idGenerator: IdGeneratorService,
        private val messageApi: MessageApi,
        private val interceptor: Interceptor
) {

  /**
   * Returns a fresh entity which can be used inside the system. It already
   * has a unique ID and can be used to persist date.
   *
   * @return A newly created entity.
   */
  fun newEntity(): Entity {
    return Entity(idGenerator.newId())
  }

  /**
   * Deletes the entity. If an entity has an active EntityActor the actor will
   * stop to operate if no more components are attached.
   *
   * @param entity The entity id remove from the memory database.
   */
  fun delete(entity: Entity) {
    Objects.requireNonNull(entity)

    LOG.trace("delete(): {}", entity)

    val eid = entity.id
    entities.lock(eid)

    try {
      // Delete all components.
      deleteAllComponents(entity)
      entities.delete(eid)

    } finally {
      entities.unlock(eid)
    }
  }

  /**
   * Deletes the entity given by its id. This is a alias for
   * [.delete].
   *
   * @param entityId Removes this entity.
   */
  fun delete(entityId: Long) {
    LOG.trace("delete(): {}", entityId)
    delete(entities[entityId])
  }

  /**
   * Returns the ID entity with the given ID.
   *
   * @param entityId Lookups this entity.
   * @return The [Entity] or NULL if no such id is stored.
   */
  fun getEntity(entityId: Long): Entity? {
    entities.lock(entityId)
    try {
      return entities[entityId]
    } finally {
      entities.unlock(entityId)
    }
  }

  /**
   * Gets all entities with the given ids.
   *
   * @param ids IDs to return all entities.
   * @return All entities which possess the given IDs.
   */
  fun getAllEntities(ids: Set<Long>): Map<Long, Entity> {
    return entities.getAll(ids)
  }

  /**
   * Creates a new component which can be used with an entity. The component
   * is not yet saved in the system and can be filled with data. When the
   * component is filled it should be attached to an entity by calling
   * [.attachComponent]. If multiple components
   * should be attached at once on the entity then
   */
  fun <T : Component> newComponent(clazz: Class<T>): T {

    val addedComp = cache.getComponent(clazz)

    if (addedComp != null) {
      return addedComp
    } else {
      val comp: Component
      try {
        val ctor = clazz.getConstructor(Long::class.javaPrimitiveType) as Constructor<Component>
        comp = ctor.newInstance(newId)
      } catch (ex: Exception) {
        LOG.error("Could not instantiate component.", ex)
        throw IllegalArgumentException(ex)
      }

      return clazz.cast(comp)
    }
  }

  /**
   * Re-attaches an existing component to an entity. The component must not be
   * owned by an entity (its entity id must be set to 0).
   */
  fun attachComponent(e: Entity?, comp: Component) {
    Objects.requireNonNull<Entity>(e)
    Objects.requireNonNull(comp)

    if (comp.entityId != 0L) {
      throw IllegalArgumentException(
              "Component is already owned by an entity. Delete/Remove it first via EntityDeleter.")
    }

    // Add component to entity and to the comp map.
    val entityId = e!!.id

    comp.setEntityId(entityId)
    e.addComponent(comp)

    updateComponent(comp)
    saveEntity(e)

    LOG.trace("Added component {} to entity id: {}", comp, e.id)

    interceptor.interceptCreated(this, e, comp)
  }

  /**
   * Attaches all the components in one go to the entity.
   */
  fun attachComponents(e: Entity, attachComponents: Collection<Component>) {
    Objects.requireNonNull(e)
    Objects.requireNonNull(components)

    LOG.trace("Attaching components: {} to entity: {}.", attachComponents, e.id)

    // Add component to entity and to the comp map.
    val entityId = e.id

    attachComponents.stream()
            .filter { c -> c.entityId != 0L && c.entityId != entityId }
            .findAny()
            .ifPresent { c ->
              throw IllegalArgumentException(
                      "Component is already attached to other entity: " + c.toString())
            }

    try {
      entities.lock(entityId)

      for (comp in attachComponents) {
        comp.setEntityId(entityId)
        e.addComponent(comp)
        internalUpdateComponent(comp)
      }

      saveEntity(e)
    } finally {
      entities.unlock(entityId)
    }

    // After all is saved intercept the created components.
    attachComponents.forEach { c -> interceptor.interceptCreated(this, e, c) }
  }

  /**
   * Saves the given component back into the database. Update of the
   * interceptors is called. If the component is not attached to an entity it
   * throws an exception.
   *
   * @param component The component to be updated into the database.
   */
  fun updateComponent(component: Component) {

    if (component.entityId == 0L) {
      throw IllegalArgumentException("Component is not attached to entity. Call attachComponent first.")
    }

    // Acquire the lock for updating the component.
    components.lock(component.id)
    try {
      // Check if the component actually needs an update.
      // Component might be null if this was called via attach component.
      // TODO This re-fetch from earlier could be avoided!
      // A save of the hash of this component could be performed upon fetching
      // if the hash is different now the component has changed.
      val oldComponent = getComponent(component.id)
      if (oldComponent != null && oldComponent == component) {
        return
      }

      internalUpdateComponent(component)
    } finally {
      components.unlock(component.id)
    }

    val ownerEntity = getEntity(component.entityId)
    interceptor.interceptUpdate(this, ownerEntity, component)
  }

  /**
   * Updates the component but does not trigger the interceptor call yet. It
   * only updates the component if
   */
  private fun internalUpdateComponent(component: Component) {
    components.lock(component.id)
    try {
      components[component.id] = component
    } finally {
      components.unlock(component.id)
    }
  }

  /**
   * Deletes a specific component from this entity. The entity reference is
   * needed since we remove the component ID also from the internal entity
   * registry.
   *
   * @param entity    The entity to delete the component from.
   * @param component The component to delete.
   */
  fun deleteComponent(entity: Entity?, component: Component) {

    LOG.trace("Removing component {} from entity {}.", component, entity)

  }

  /**
   * Alias for [.deleteComponent].
   */
  fun deleteComponent(entityId: Long, clazz: Class<out Component>) {
    getComponent<out Component>(entityId, clazz)
            .ifPresent({ c -> deleteComponent(getEntity(entityId), c) })
  }

  /**
   * Removes all the components from the entity.
   *
   * @param entity The entity to remove all components from.
   */
  fun deleteAllComponents(entity: Entity) {

    val componentIds = HashSet(entity.getComponentIds())

    for (componentId in componentIds) {

      val comp = components[componentId]

      if (comp == null) {
        LOG.warn("Component with ID {} not attached to entity {}. Skipping removal.", componentId,
                entity.id)
        continue
      }

      LOG.trace("Preparing to remove: {} from entity: {}", comp, entity)
      prepareComponentRemove(entity, comp)

    }

    saveEntity(entity)
  }

  /**
   * Checks if the entity has the given component.
   *
   * @param entity The entity to check.
   * @param clazz  The component class to check for.
   * @return TRUE if the entity has the component. FALSE otherwise.
   */
  fun hasComponent(entity: Entity, clazz: Class<out Component>): Boolean {
    Objects.requireNonNull(entity)
    Objects.requireNonNull(clazz)

    return entity.getComponentId(clazz) !== 0
  }

  /**
   * Returns all components of this entity.
   *
   * @param entity The entity which components will get returned.
   * @return A collection of all components from this entity.
   */
  fun getAllComponents(entity: Entity): Collection<Component> {

    return Objects.requireNonNull(entity)
            .getComponentIds()
            .stream()
            .map(???({ components.get() }))
    .collect(Collectors.toList<T>())
  }

  /**
   * Returns the component with the given ID or null if the component does not
   * exist.
   *
   * @param componentId The component ID to retrieve the component.
   * @return The requested component or NULL if the component id does not
   * exist.
   */
  fun getComponent(componentId: Long): Component? {
    return components[componentId]
  }

  /**
   * Alias to [.getComponent].
   *
   * @param entityId The ID of the entity.
   * @param clazz    The class of the [Component] to retrieve.
   * @return The component if it was attached to this entity.
   */
  fun <T : Component> getComponent(entityId: Long, clazz: Class<T>): Optional<T> {

    val e = getEntity(entityId)

    if (e == null) {
      LOG.warn("Entity {} not found for component lookup: {}", entityId, clazz)
      return Optional.empty()
    }

    return getComponent(e, clazz)
  }

  /**
   * Returns the requested component of the entity. If the component does not
   * exist the optional will be empty.
   *
   * @param e     The entity to request its component.
   * @param clazz The component class which component is requested.
   * @return The optional containing the component or nothing if the entity
   * has not this component attached.
   */
  fun <T : Component> getComponent(e: Entity?, clazz: Class<T>?): Optional<T> {
    if (e == null || clazz == null) {
      return Optional.empty()
    }

    LOG.trace("getComponent(): {}, {}", e, clazz)

    val compId = e.getComponentId(clazz)

    if (compId == 0L) {
      return Optional.empty()
    }

    val comp: Component?

    components.lock(compId)
    try {
      comp = components[compId]
    } finally {
      components.unlock(compId)
    }

    if (comp == null) {
      LOG.error("Component {} (id: {}) owned by entity {} can not be found.", clazz, compId, e)
      e.removeComponent(compId)
      saveEntity(e)
      return Optional.empty()
    }

    if (!comp.javaClass.isAssignableFrom(clazz)) {
      LOG.error("Loaded component {} owned by entity {} has wrong class.", comp, e)
      e.removeComponent(comp)
      saveEntity(e)
      return Optional.empty()
    }

    return Optional.of(clazz.cast(comp))
  }

  /**
   * Returns the component if a component of this type was already attached to
   * the entity. Otherwise it creates a new component and returns it
   * afterwards.
   *
   * @param e     The entity to
   * @param clazz The component class which is requested.
   * @return The requested of created component.
   */
  fun <T : Component> getComponentOrCreate(e: Entity?, clazz: Class<T>): T {
    val optComp = getComponent(e, clazz)
    if (optComp.isPresent) {
      return optComp.get()
    } else {
      val comp = newComponent(clazz)
      attachComponent(e, comp)
      return comp
    }
  }

  /**
   * Alias for [.getComponentOrCreate].
   *
   * @param entityId The entity id to fetch or create a component for.
   * @param clazz    The component to create.
   * @return The component.
   */
  fun <T : Component> getComponentOrCreate(entityId: Long, clazz: Class<T>): T {
    return getComponentOrCreate(getEntity(entityId), clazz)
  }

  companion object {
    const val ECS_ENTITY_MAP_KEY = "entities"
  }
}
