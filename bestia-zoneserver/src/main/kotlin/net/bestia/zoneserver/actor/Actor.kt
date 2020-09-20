package net.bestia.zoneserver.actor

import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

/**
 * As classes can not be null we sadly have to introduce a placeholder for the
 * ActorComponent annotation if we don't want to handle any component when
 * we don't want to loose type safety.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
annotation class Actor