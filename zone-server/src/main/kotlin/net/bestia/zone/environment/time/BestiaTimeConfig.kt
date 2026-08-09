package net.bestia.zone.environment.time

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Instant

/**
 * @property worldEpoch when this world's Bestia-time clock starts counting from. Left unset - which is
 * the normal case - the clock anchors to `PersistedWorld.createdAt` instead, so the calendar survives a
 * restart the way the world it describes does. See [BestiaClock.worldEpoch]. Set it to pin a deployment's
 * calendar to a fixed instant regardless of when its world row was written.
 * @property speedFactor how many Bestia-hours pass per real-world hour. See [BestiaDateTime.SPEED_FACTOR].
 */
@ConfigurationProperties(prefix = "world-time")
data class BestiaTimeConfig(
  val worldEpoch: Instant? = null,
  val speedFactor: Double = BestiaDateTime.SPEED_FACTOR,
)
