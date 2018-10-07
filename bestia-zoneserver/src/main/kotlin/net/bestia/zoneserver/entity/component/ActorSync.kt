package net.bestia.zoneserver.entity.component

import akka.actor.AbstractActor
import kotlin.reflect.KClass

/**
 * Added to a component this annotation will automatically start a Component
 * managing actor upon installing this component and automatically remove the
 * actor if the component is deleted from the entity.
 *
 * @author Thomas Felix
 */
@Retention(AnnotationRetention.RUNTIME)
annotation class ActorSync<T : AbstractActor>(
    /**
     * Fully qualified actor name to get spawned if this component is
     * installed.
     */
    val value: KClass<T>
)
