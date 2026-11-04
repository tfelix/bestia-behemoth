package net.bestia.zone.world

/**
 * Marks a test class that reads [GeneratedWorlds.of]. Those worlds are large, so [LargeWorldsFirst] runs these
 * classes back to back and [ReleaseLargeWorldsExtension] lets the worlds go when the group is done.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReadsLargeWorlds
