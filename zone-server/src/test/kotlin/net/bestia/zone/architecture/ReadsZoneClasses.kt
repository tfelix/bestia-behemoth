package net.bestia.zone.architecture

/** Marks a test class that reads [ZoneClasses], so it runs in that fixture's group. See [net.bestia.zone.fixtures.HeavyFixture]. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReadsZoneClasses
