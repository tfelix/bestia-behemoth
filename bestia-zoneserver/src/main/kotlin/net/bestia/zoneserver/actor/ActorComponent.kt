package net.bestia.zoneserver.actor

import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component


@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Component
@Scope("prototype")
annotation class ActorComponent