package net.bestia.zone.travel

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * @property knowledgeTtl how long a traveller trusts a blockage it saw. After it the route is tried again.
 * @property failureTtl how long a traveller trusts a blockage it only ran into, without seeing why.
 * @property pathfindsPerTick local searches all journeys together may run per tick, beside the AI's own.
 * @property surveysPerTick kilometre blocks of the water grid all journeys together may survey per tick, each
 *   some ten milliseconds of the tick's fifty. A way into new country waits a tick or a few for the rest.
 * @property repathInterval how often a leg is searched again while it is walked, so new obstacles are met.
 * @property stallAfter how long without getting nearer before the traveller decides the way ahead is blocked.
 * @property giveUpAfter how long without getting nearer before the journey fails as stuck.
 * @property maxWaypoints the most stops one journey may have.
 * @property maxSavedRoutes the most routes one master keeps.
 */
@ConfigurationProperties(prefix = "travel")
data class TravelConfig(
  val knowledgeTtl: Duration = Duration.ofDays(21),
  val failureTtl: Duration = Duration.ofDays(1),
  val pathfindsPerTick: Int = 8,
  val surveysPerTick: Int = 2,
  val repathInterval: Duration = Duration.ofSeconds(2),
  val stallAfter: Duration = Duration.ofSeconds(10),
  val giveUpAfter: Duration = Duration.ofSeconds(30),
  val maxWaypoints: Int = 16,
  val maxSavedRoutes: Int = 32,
) {
  init {
    require(!knowledgeTtl.isNegative && !failureTtl.isNegative) { "Route knowledge cannot lapse before it is learned" }
    require(pathfindsPerTick > 0) { "Travel needs at least one search per tick" }
    require(surveysPerTick > 0) { "Travel needs at least one survey per tick, or no way round water is ever found" }
    require(stallAfter < giveUpAfter) { "A traveller must try another way before it gives up" }
    require(maxWaypoints > 0) { "A journey needs at least its destination" }
  }
}
