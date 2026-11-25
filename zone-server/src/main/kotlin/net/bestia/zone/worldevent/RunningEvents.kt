package net.bestia.zone.worldevent

import net.bestia.zone.casting.sense.BrewingEvents
import org.springframework.stereotype.Component

/**
 * Where the director's events are running, as of its last run, for the senses that feel them. Written off the
 * tick, read on it, so it swaps a whole list.
 */
@Component
class RunningEvents : BrewingEvents {

  @Volatile
  private var positions: List<Pair<Double, Double>> = emptyList()

  override fun positions(): List<Pair<Double, Double>> = positions

  fun replace(positions: List<Pair<Double, Double>>) {
    this.positions = positions
  }
}
