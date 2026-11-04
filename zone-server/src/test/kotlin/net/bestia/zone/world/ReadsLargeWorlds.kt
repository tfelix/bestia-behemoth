package net.bestia.zone.world

/**
 * Marks a test class that reads [GeneratedWorlds.of]. Those worlds are large, so these classes run back to back
 * and the worlds are let go when the group is done. See [net.bestia.zone.fixtures.HeavyFixture].
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReadsLargeWorlds
