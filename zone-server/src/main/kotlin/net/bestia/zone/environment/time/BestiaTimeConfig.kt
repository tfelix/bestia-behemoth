package net.bestia.zone.environment.time

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Instant

/**
 * @property worldEpoch when this world's Bestia-time clock starts counting from. Left unset,
 * it defaults to the server's boot instant - matching the docs' "Bestia-time starts at the
 * creation of the Bestia world", and fitting since the dev DB schema is recreated on every boot.
 * @property speedFactor how many Bestia-hours pass per real-world hour. See [BestiaDateTime.SPEED_FACTOR].
 */
@ConfigurationProperties(prefix = "world-time")
data class BestiaTimeConfig(
  val worldEpoch: Instant? = null,
  val speedFactor: Double = BestiaDateTime.SPEED_FACTOR,
)
