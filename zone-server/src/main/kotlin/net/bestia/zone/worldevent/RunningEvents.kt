package net.bestia.zone.worldevent

import net.bestia.zone.casting.sense.BrewingEvents
import org.springframework.stereotype.Component

/**
 * The director's events as of its last run, for the senses that feel them. Written off the tick, read on it, so it
 * swaps a whole list.
 */
@Component
class RunningEvents : BrewingEvents {

  @Volatile
  private var running: List<BrewingEvents.Brewing> = emptyList()

  override fun brewing(): List<BrewingEvents.Brewing> = running

  fun replace(running: List<BrewingEvents.Brewing>) {
    this.running = running
  }
}
