package net.bestia.zone.ecs

/**
 * Determines if this class gets used as an injectable parameter for the ECS system.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ZoneInjectable