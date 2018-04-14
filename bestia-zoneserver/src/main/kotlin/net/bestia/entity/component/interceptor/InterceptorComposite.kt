package net.bestia.entity.component.interceptor

import mu.KotlinLogging
import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.Component
import java.util.*

private val LOG = KotlinLogging.logger {  }

/**
 * Main class for collecting and triggering component interaction interceptions.
 *
 * @author Thomas Felix
 */
class InterceptorComposite(
        interceptors: List<BaseComponentInterceptor<out Component>>
) : Interceptor {

  private val interceptors: MutableMap<Class<out Component>, MutableList<BaseComponentInterceptor<*>>> = mutableMapOf()
  private val defaultInterceptors: MutableList<BaseComponentInterceptor<*>> = mutableListOf()

  init {
    interceptors.forEach { this.addInterceptor(it) }
  }

  /**
   * Adds an interceptor which gets notified if certain components will
   * change. He then can perform actions like update the clients in range
   * about the occurring component change.
   *
   * @param interceptor The intercepter to listen to certain triggering events.
   */
  fun addInterceptor(interceptor: BaseComponentInterceptor<out Component>) {
    Objects.requireNonNull(interceptor)
    LOG.debug("Adding interceptor: {}.", interceptor.javaClass.name)

    val triggerType = interceptor.triggerType

    if (!interceptors.containsKey(triggerType)) {
      interceptors[triggerType] = mutableListOf(interceptor)
    }

    interceptors[triggerType]!!.add(interceptor)
  }

  /**
   * The default interceptor are called upon each component update. Regardless of their type.
   * Thus they are handy is no type specific components need to be scanned.
   */
  fun addDefaultInterceptor(interceptor: BaseComponentInterceptor<*>) {
    defaultInterceptors.add(interceptor)
  }

  /**
   * Checks if the entity owns the component.
   */
  private fun dontOwnComponent(e: Entity, c: Component): Boolean {
    if (e.id != c.entityId) {
      LOG.warn("Component {} is not owned by entity: {}.", c, e)
      return true
    }

    return false
  }

  override fun interceptUpdate(entityService: EntityService, entity: Entity, component: Component) {
    if (dontOwnComponent(entity, component)) {
      return
    }

    defaultInterceptors.forEach { i ->
      if (i.triggerType == component.javaClass) {
        i.triggerUpdateAction(entityService, entity, component)
      }
    }

    // Check possible interceptors.
    if (interceptors.containsKey(component.javaClass)) {
      LOG.debug("Intercepting update component {} for: {}.", component, entity)

      interceptors[component.javaClass]?.forEach { intercep -> intercep.triggerUpdateAction(entityService, entity, component) }
    }
  }

  override fun interceptCreated(entityService: EntityService, entity: Entity, component: Component) {
    if (dontOwnComponent(entity, component)) {
      return
    }

    defaultInterceptors.forEach { i ->
      if (i.triggerType == component.javaClass) {
        i.triggerCreateAction(entityService, entity, component)
      }
    }

    if (interceptors.containsKey(component.javaClass)) {
      LOG.debug("Intercepting created component {} for: {}.", component, entity)

      interceptors[component.javaClass]?.forEach { intercep -> intercep.triggerCreateAction(entityService, entity, component) }
    }
  }

  override fun interceptDeleted(entityService: EntityService, entity: Entity, component: Component) {
    if (dontOwnComponent(entity, component)) {
      return
    }

    defaultInterceptors.forEach { i ->
      if (i.triggerType == component.javaClass) {
        i.triggerDeleteAction(entityService, entity, component)
      }
    }

    // Check possible interceptors.
    if (interceptors.containsKey(component.javaClass)) {
      LOG.debug("Intercepting update component {} for: {}.", component, entity)

      interceptors[component.javaClass]?.forEach { intercep -> intercep.triggerDeleteAction(entityService, entity, component) }
    }
  }
}
