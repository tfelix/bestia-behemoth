package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActor
import akka.actor.ActorContext
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.entity.EntityService
import net.bestia.entity.component.ActorSync
import net.bestia.entity.component.Component
import net.bestia.zoneserver.actor.SpringExtension

private val LOG = KotlinLogging.logger { }

/**
 * Depending on the given component ID this factory will create an actor
 * suitable for the component. This is done by checking the annotation
 * of the component.
 *
 * @author Thomas Felix
 */
@org.springframework.stereotype.Component
class EntityComponentActorFactory(
        private val entityService: EntityService
) {

  /**
   * Starts a component actor which is responsible for managing continues
   * callback to some component code.
   *
   * @param componentId
   * The ID of the already saved and existing component to create
   * an actor for.
   * @return The created actor or null if something went wrong.
   */
  fun startActor(ctx: ActorContext, componentId: Long): ActorRef? {
    val comp = entityService.getComponent(componentId)

    if (comp == null) {
      LOG.warn("Component {} does not exist. Can not build component actor for it.", componentId)
      return null
    }

    return startActorByAnnotation(ctx, comp)
  }

  fun startActor(ctx: ActorContext, component: Component): ActorRef? {
    return startActorByAnnotation(ctx, component)
  }

  private fun startActorByAnnotation(ctx: ActorContext, comp: Component): ActorRef? {
    if (!comp.javaClass.isAnnotationPresent(ActorSync::class.java)) {
      LOG.warn("Component {} (id: {}) has no ComponentActor annotation. Can not create Actor.",
              comp.javaClass.name, comp.id)
      return null
    }

    val compActor = comp.javaClass.getAnnotation(ActorSync::class.java)
    try {
      val actorClass = Class.forName(compActor.value) as Class<AbstractActor>

      val takesComponentAsArg = actorClass.declaredConstructors.any {
        it.parameterCount == 1 && Component::class.java.isAssignableFrom(it.parameterTypes[0])
      }

      val actorRef = if(takesComponentAsArg) {
         SpringExtension.actorOf(ctx, actorClass, null, comp)
      } else {
        SpringExtension.actorOf(ctx, actorClass, null, comp.entityId, comp.id)
      }


      LOG.debug("Starting ComponentActor: {} ({}) for entity: {}.",
              actorClass.simpleName,
              comp.id,
              comp.entityId)

      return actorRef
    } catch (e: ClassNotFoundException) {
      LOG.warn("Could not start ComponentActor. Class not found: {}.", compActor.value)
      return null
    }

  }
}
