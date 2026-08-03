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

  /** How much faster Bestia time runs than real time. Read by anything converting one to the other. */
  val speedFactor: Double get() = config.speedFactor

  /**
   * [from] advanced by [bestiaSeconds] of in-game time.
   *
   * Exists so a forecast can be asked for without a second copy of the epoch arithmetic - the weather field is
   * a pure function of the time it is handed, so "the weather in an hour" is this plus one evaluation.
   */
  fun after(from: BestiaDateTime, bestiaSeconds: Long): BestiaDateTime =
    BestiaDateTime.since(
      java.time.Duration.ofSeconds(
        ((from.absoluteDay * SECONDS_PER_BESTIA_DAY + bestiaSeconds) / config.speedFactor).toLong()
      ),
      config.speedFactor
    )

  private companion object {
    private const val SECONDS_PER_BESTIA_DAY =
      BestiaDateTime.HOURS_PER_DAY * 3_600.0
  }
}
