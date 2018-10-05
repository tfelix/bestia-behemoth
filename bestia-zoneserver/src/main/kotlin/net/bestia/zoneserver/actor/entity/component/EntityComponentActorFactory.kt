package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActor
import akka.actor.ActorContext
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.zoneserver.entity.component.Component
import net.bestia.zoneserver.actor.SpringExtension
import org.reflections.Reflections
import kotlin.reflect.KClass
import org.springframework.stereotype.Component as SpringComponent

private val LOG = KotlinLogging.logger { }

/**
 * Depending on the given component ID this factory will create an actor
 * suitable for the component. This is done by checking the annotation
 * of the component.
 *
 * @author Thomas Felix
 */
@SpringComponent
class EntityComponentActorFactory {

  private val componentToActorClass: Map<KClass<out Component>, Class<out AbstractActor>>

  init {
    val reflections = Reflections("net.bestia.zoneserver.actor")
    val annotated = reflections.getTypesAnnotatedWith(HandlesComponent::class.java)
    componentToActorClass = annotated.asSequence()
        .filter { it is AbstractActor }
        .map {
          @Suppress("UNCHECKED_CAST")
          it as Class<out AbstractActor>
        }
        .map {
          val annotation = it.getAnnotation(HandlesComponent::class.java)
          annotation.component to it
        }.toList()
        .toMap()
  }

  fun startActor(ctx: ActorContext, component: Component): ActorRef? {
    return startActorByAnnotation(ctx, component)
  }

  private fun startActorByAnnotation(
      ctx: ActorContext,
      component: Component
  ): ActorRef? {

    try {
      val actorClass = componentToActorClass[component::class]!!
      val takesComponentAsArg = actorClass.declaredConstructors.any {
        it.parameterCount == 1 && Component::class.java.isAssignableFrom(it.parameterTypes[0])
      }

      val actorRef = if (takesComponentAsArg) {
        SpringExtension.actorOf(ctx, actorClass, component)
      } else {
        null
      }

      LOG.debug { "Starting ComponentActor: ${actorClass.simpleName} for entity: ${component.entityId}." }

      return actorRef
    } catch (e: ClassNotFoundException) {
      LOG.warn { "Could not start ComponentActor. Class not found: ${component.javaClass}." }
      return null
    }
  }
}
