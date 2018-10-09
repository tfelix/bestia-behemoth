package net.bestia.zoneserver.actor.entity.component

import akka.actor.AbstractActor
import akka.actor.ActorContext
import akka.actor.ActorRef
import mu.KotlinLogging
import net.bestia.zoneserver.entity.component.Component
import net.bestia.zoneserver.actor.SpringExtension
import org.reflections.Reflections
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import kotlin.reflect.KClass
import org.springframework.stereotype.Component as SpringComponent

private val LOG = KotlinLogging.logger { }

/**
 * Depending on the given component class this factory will create an actor
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
        .filter { AbstractActor::class.java.isAssignableFrom(it) }
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
  ): ActorRef {
    LOG.debug { "Starting actor for component $component" }

    val actorClass = componentToActorClass[component::class]
        ?: throw IllegalArgumentException("Could not find actor for component class: ${component.javaClass}")

    val hasCtorWithComponentAsArg = actorClass.declaredConstructors.any {
      it.parameterTypes.any { paramType -> Component::class.java.isAssignableFrom(paramType) }
    }

    return if (hasCtorWithComponentAsArg) {
      SpringExtension.actorOf(ctx, actorClass, component)
    } else {
      throw IllegalStateException("Actor $actorClass does not use component ${component.javaClass} as ctor argument.")
    }
  }
}
