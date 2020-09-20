package net.bestia.zoneserver.actor

import net.bestia.zoneserver.actor.entity.transmit.NoOpTransmitFilter
import net.bestia.zoneserver.actor.entity.transmit.TransmitFilter
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import kotlin.reflect.KClass
import net.bestia.zoneserver.entity.component.Component as BestiaComponent

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
annotation class ActorComponent(
    val component: KClass<out BestiaComponent>,
    val transmitFilter: KClass<out TransmitFilter> = NoOpTransmitFilter::class
)

