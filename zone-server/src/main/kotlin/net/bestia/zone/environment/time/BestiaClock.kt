package net.bestia.zone.environment.time

import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
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

  /**
   * How far [jumpTo] has moved the calendar away from the real elapsed time, and **only in this process**.
   *
   * A shift rather than a rewritten epoch, which is what makes the change memory-only without any special
   * handling: [worldEpoch] is derived from a database row and stays untouched, so a restart simply loses this
   * and the calendar returns to where the world's age puts it. Nothing has to remember to clean up.
   *
   * The clock keeps *running* across a jump - this is added to the elapsed duration, not substituted for it -
   * so `/date 02:00` sets the time to two in the morning and then two in the morning goes on becoming three.
   * A GM checking what a night looks like wants a night, not a paused frame of one.
   *
   * `@Volatile` because chat is dispatched on a Netty worker thread while the ECS systems that read [now] are
   * on `zone-tick`. A `Duration` reference publishes safely; the object itself is immutable.
   */
  @Volatile
  private var shift: Duration = Duration.ZERO

  fun now(): BestiaDateTime =
    BestiaDateTime.at(worldEpoch, Instant.now(clock).plus(shift), config.speedFactor)

  /**
   * Moves the calendar so that [now] reads [target], and keeps it running from there.
   *
   * In memory only - see [shift]. Everything downstream picks it up for free, because the weather field, the
   * AI's activity cycle and the temperature model are all pure functions of the time [now] hands them; there
   * is no cached "current weather" to invalidate.
   *
   * @return what the clock actually reads afterwards, which is [target] to the second
   */
  fun jumpTo(target: BestiaDateTime): BestiaDateTime {
    // The elapsed real time that *would* have produced this date, minus the elapsed real time there actually
    // is. Millisecond resolution is far finer than the second the calendar is quantised to, even at a speed
    // factor in the hundreds.
    val wantedReal = Duration.ofMillis((target.absoluteSecond * 1_000L / config.speedFactor).toLong())
    val actualReal = Duration.between(worldEpoch, Instant.now(clock))

    shift = wantedReal.minus(actualReal)

    return now()
  }

  /** Drops any [jumpTo], putting the calendar back where the world's real age puts it. */
  fun resetToRealTime(): BestiaDateTime {
    shift = Duration.ZERO

    return now()
  }

  /** Whether [jumpTo] has moved the calendar off real time. Reported by `/date` so a wrong sky is explicable. */
  val isShifted: Boolean get() = !shift.isZero

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
