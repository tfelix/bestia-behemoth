package net.bestia.zone.travel

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * @property knowledgeTtl how long a traveller trusts what it saw of a route. A bridge seen down is avoided for
 *   this long unless somebody sees it standing again; after it, the route is tried again.
 */
@ConfigurationProperties(prefix = "travel")
data class TravelConfig(
  val knowledgeTtl: Duration = Duration.ofDays(21),
) {
  init {
    require(!knowledgeTtl.isNegative) { "Route knowledge cannot expire before it is learned" }
  }
}
