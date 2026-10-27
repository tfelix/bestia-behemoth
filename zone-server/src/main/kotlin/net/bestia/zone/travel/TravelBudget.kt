package net.bestia.zone.travel

import net.bestia.zone.navigation.region.SurveyBudget
import org.springframework.stereotype.Component

/**
 * The local searches journeys may run this tick, apart from the AI's: see [TravelConfig.pathfindsPerTick]. And
 * the water grid's blocks they may survey: see [TravelConfig.surveysPerTick].
 */
@Component
class TravelBudget(private val config: TravelConfig) {

  private var remaining = 0
  private var surveysLeft = 0

  /** Spent by [net.bestia.zone.navigation.region.RegionGrid] as it reaches country it has not surveyed. */
  val surveys = SurveyBudget {
    if (surveysLeft <= 0) return@SurveyBudget false
    surveysLeft--
    true
  }

  fun refill() {
    remaining = config.pathfindsPerTick
    surveysLeft = config.surveysPerTick
  }

  fun trySpend(): Boolean {
    if (remaining <= 0) return false
    remaining--
    return true
  }
}
