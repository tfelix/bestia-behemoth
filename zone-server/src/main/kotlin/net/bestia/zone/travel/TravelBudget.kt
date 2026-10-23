package net.bestia.zone.travel

import org.springframework.stereotype.Component

/** The local searches journeys may run this tick, apart from the AI's: see [TravelConfig.pathfindsPerTick]. */
@Component
class TravelBudget(private val config: TravelConfig) {

  private var remaining = 0

  fun refill() {
    remaining = config.pathfindsPerTick
  }

  fun trySpend(): Boolean {
    if (remaining <= 0) return false
    remaining--
    return true
  }
}
