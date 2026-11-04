package net.bestia.zone.fixtures

import net.bestia.zone.architecture.ReadsZoneClasses
import net.bestia.zone.architecture.ZoneClasses
import net.bestia.zone.world.GeneratedWorlds
import net.bestia.zone.world.ReadsLargeWorlds

/**
 * A fixture too large to hold for the whole run. The classes marked with its [marker] run back to back
 * ([HeavyFixturesFirst]), and the fixture is let go when the first class outside the group starts
 * ([ReleaseHeavyFixturesExtension]). The declaration order is the run order.
 *
 * [holder] and [readers] name the calls that read the fixture, so `TestWorldRulesTest` can hold every reader to
 * the marker.
 */
enum class HeavyFixture(
  val marker: Class<out Annotation>,
  val holder: Class<*>,
  val readers: Set<String>,
  private val letGo: () -> Unit,
) {
  /** First, so the worlds are freed before the Spring context boots. */
  LARGE_WORLDS(ReadsLargeWorlds::class.java, GeneratedWorlds::class.java, setOf("of"), GeneratedWorlds::releaseLarge),
  ZONE_CLASSES(ReadsZoneClasses::class.java, ZoneClasses::class.java, setOf("getMain", "getTests"), ZoneClasses::release);

  fun release() {
    letGo()
  }

  companion object {
    fun readBy(testClass: Class<*>): HeavyFixture? {
      return entries.firstOrNull { testClass.isAnnotationPresent(it.marker) }
    }
  }
}
