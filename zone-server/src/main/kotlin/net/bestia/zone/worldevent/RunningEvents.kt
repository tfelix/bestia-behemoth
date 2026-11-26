package net.bestia.zone.worldevent

import net.bestia.zone.casting.sense.BrewingEvents
import org.springframework.stereotype.Component

/**
 * What is brewing in the world, for the senses that feel it: the director's events as of its last run, and what any
 * other source adds. Written off the tick, read on it, so it swaps a whole list.
 */
@Component
class RunningEvents : BrewingEvents {

  private val bySource = HashMap<String, List<BrewingEvents.Brewing>>()

  @Volatile
  private var running: List<BrewingEvents.Brewing> = emptyList()

  override fun brewing(): List<BrewingEvents.Brewing> = running

  fun replace(running: List<BrewingEvents.Brewing>) {
    replace(DIRECTOR, running)
  }

  @Synchronized
  fun replace(source: String, brewing: List<BrewingEvents.Brewing>) {
    bySource[source] = brewing
    running = bySource.values.flatten()
  }

  private companion object {
    const val DIRECTOR = "director"
  }
}
