package net.bestia.zone.environment.time

import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * Converts real-world time into the current [BestiaDateTime] for this world instance. The
 * world's creation instant anchors the conversion; see [BestiaDateTime] for the day/month/year math.
 */
@Service
class BestiaClock(
  private val config: BestiaTimeConfig,
  private val worldService: WorldService,
  private val clock: Clock = Clock.systemUTC(),
) {

  /**
   * When this world's calendar starts counting from: the instant the world row was written.
   *
   * **Not the server's boot instant**, which is what this used to be. zone-server runs MariaDB with
   * `ddl-auto: update` and the world survives a restart, so anchoring here to `Instant.now()` made the
   * terrain, the masters and their positions persist while the calendar snapped back to Year 1 Day 1 on
   * every boot. That is worse than a wrong date on a UI: the weather field is `f(seed, region, t)` with
   * `t` = [BestiaDateTime.absoluteDay], so a resetting epoch replayed the same weather after every
   * restart, and `WEATHER_SENSE` forecast from a timeline that had already happened.
   *
   * `PersistedWorld.createdAt` is the right anchor because it moves exactly when it should: it is rewritten
   * by `WorldProvisioning.recreate()`, so regenerating a world does start its calendar over, and only then.
   *
   * `by lazy` rather than an initialiser, and that is required rather than stylistic: `WorldService.load()`
   * runs in a boot runner at `@Order(1)`, so `record` throws at bean-construction time. `WeatherService`
   * reads `worldService.generated` lazily for the same reason.
   *
   * [BestiaTimeConfig.worldEpoch] still wins when set, which is what the tests and a pinned-calendar
   * deployment use.
   */
  private val worldEpoch: Instant by lazy { config.worldEpoch ?: worldService.record.createdAt }

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
