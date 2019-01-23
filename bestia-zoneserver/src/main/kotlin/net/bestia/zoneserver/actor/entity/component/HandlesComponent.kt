package net.bestia.zoneserver.actor.entity.component

import net.bestia.zoneserver.entity.component.Component
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@MustBeDocumented
@Deprecated("Use ActorComponent annotation instead")
annotation class HandlesComponent(
    val component: KClass<out Component>
)