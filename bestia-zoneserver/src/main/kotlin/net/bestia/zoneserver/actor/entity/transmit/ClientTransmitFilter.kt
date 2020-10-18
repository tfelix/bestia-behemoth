package net.bestia.zoneserver.actor.entity.transmit

import kotlin.reflect.KClass

/**
 * Allows for components to get manages via a filter if they should be broadcasted to multiple
 * clients in the game.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ClientTransmitFilter(
    val value: KClass<out TransmitFilter> = NoOpTransmitFilter::class
)