package net.bestia.zone.travel

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * @property knowledgeTtl how long a traveller trusts a blockage it saw. After it the route is tried again.
 * @property failureTtl how long a traveller trusts a blockage it only ran into, without seeing why.
 * @property pathfindsPerTick local searches all journeys together may run per tick, beside the AI's own.
 * @property repathInterval how often a leg is searched again while it is walked, so new obstacles are met.
 * @property stallAfter how long without getting nearer before the traveller decides the way ahead is blocked.
 * @property giveUpAfter how long without getting nearer before the journey fails as stuck.
 * @property maxWaypoints the most stops one journey may have.
 */
@ConfigurationProperties(prefix = "travel")
data class TravelConfig(
  val knowledgeTtl: Duration = Duration.ofDays(21),
  val failureTtl: Duration = Duration.ofDays(1),
  val pathfindsPerTick: Int = 8,
  val repathInterval: Duration = Duration.ofSeconds(2),
  val stallAfter: Duration = Duration.ofSeconds(10),
  val giveUpAfter: Duration = Duration.ofSeconds(30),
  val maxWaypoints: Int = 16,
) {
  init {
    require(!knowledgeTtl.isNegative && !failureTtl.isNegative) { "Route knowledge cannot lapse before it is learned" }
    require(pathfindsPerTick > 0) { "Travel needs at least one search per tick" }
    require(stallAfter < giveUpAfter) { "A traveller must try another way before it gives up" }
    require(maxWaypoints > 0) { "A journey needs at least its destination" }
  }
}
