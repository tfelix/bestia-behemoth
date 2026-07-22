package net.bestia.zone.environment.time

import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * Converts real-world time into the current [BestiaDateTime] for this world instance. The
 * world's creation instant ([BestiaTimeConfig.worldEpoch]) anchors the conversion; see
 * [BestiaDateTime] for the day/month/year math.
 */
@Service
class BestiaClock(
  private val config: BestiaTimeConfig,
  private val clock: Clock = Clock.systemUTC(),
) {
  private val worldEpoch: Instant = config.worldEpoch ?: Instant.now(clock)

  fun now(): BestiaDateTime = BestiaDateTime.at(worldEpoch, Instant.now(clock), config.speedFactor)
}
