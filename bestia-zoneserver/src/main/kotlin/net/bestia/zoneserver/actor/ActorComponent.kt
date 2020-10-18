package net.bestia.zoneserver.actor

import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import kotlin.reflect.KClass
import net.bestia.zoneserver.entity.component.Component as BestiaComponent

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Component
@MustBeDocumented
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
annotation class ActorComponent(
    val component: KClass<out BestiaComponent>
)

