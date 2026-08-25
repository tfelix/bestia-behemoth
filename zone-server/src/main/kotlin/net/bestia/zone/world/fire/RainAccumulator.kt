package net.bestia.zone.world.fire

import net.bestia.worldgen.climate.WeatherKind
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.environment.time.BestiaDateTime
import net.bestia.zone.environment.weather.WeatherService
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * How much rain has fallen on one chunk column since the fire that scarred it.
 *
 * ### Why this is computed rather than accumulated
 *
 * `WeatherModel` is `f(seed, region, dayOfWorld)` with **no state**, which is what lets
 * `WeatherService.forecast` step `t` forward and get the answer a player will actually see. Rainfall over a
 * *past* window is the same evaluation, so a scar needs to store nothing but when it started.
 *
 * `WeatherService`'s own KDoc is the argument for preferring this to a stored total: *"a process-lifetime memo
 * cannot go stale while a table can"*. A column would also make every regrowth step a database write, where
 * this makes none until a scar is gone.
 *
 * ### The window is clamped, and the bias is deliberate
 *
 * Integrating from an arbitrarily old instant is O(age), and a scar in a desert never heals - so its window
 * would grow without bound, which is the shape this codebase refuses on the tick thread. [CATCH_UP_DAYS] caps
 * the *first* pass after boot; every pass after that continues from where the last one stopped, so steady
 * state is one hourly step per scarred column per Bestia hour.
 *
 * Past the cap a scar is treated as having taken no rain, so it **lingers** rather than healing. That is the
 * safe direction: the other one deletes a mark a player made on the world.
 */
@Service
class RainAccumulator(
  private val worldService: WorldService,
  private val weather: WeatherService,
  private val clock: BestiaClock,
) {

  /** Millimetres of rain worth one full pass of erosion. See [HEAL_RAIN_MM] - it is not "gone entirely". */
  val healRainMm: Double get() = HEAL_RAIN_MM

  /**
   * Folds the rain since [scar]'s mark into it and advances the mark.
   *
   * Snow does not count. It is precipitation and it does not green a burn - it sits on top of one - so a
   * blizzard heals nothing here, which is also why a scar above the snow line stays put through a winter.
   */
  fun advance(columnKey: Long, scar: Scar) {
    val chunkSize = worldService.config.chunkSize

    // The middle of the column. A chunk is 32 m against a 16 km weather region, so which cell is asked cannot
    // matter - taking the centre rather than a corner just stops a column near a region boundary being
    // attributed differently on different passes.
    val voxelX = ScorchRegistry.chunkXOf(columnKey).toLong() * chunkSize + chunkSize / 2
    val voxelY = ScorchRegistry.chunkYOf(columnKey).toLong() * chunkSize + chunkSize / 2

    val nowSecond = clock.now().absoluteSecond
    var cursor = maxOf(scar.integratedThroughSecond, nowSecond - CATCH_UP_SECONDS)

    val rainPerHour = worldService.generated.params.weather.rainRateMmPerDay / HOURS_PER_DAY

    var steps = 0
    while (cursor < nowSecond && steps < MAX_STEPS_PER_PASS) {
      val state = weather.at(voxelX, voxelY, ELEVATION_METRES, at(cursor)).state

      if (state.intensity > 0.0 && state.kind !in FROZEN) {
        scar.rainMm += state.intensity * rainPerHour
      }

      cursor += BESTIA_SECONDS_PER_HOUR
      steps++
    }

    scar.integratedThroughSecond = minOf(cursor, nowSecond)
  }

  /** The Bestia date/time at an absolute Bestia second. */
  private fun at(absoluteSecond: Long): BestiaDateTime =
    clock.after(BestiaDateTime.since(Duration.ZERO, clock.speedFactor), absoluteSecond)

  private companion object {
    const val BESTIA_SECONDS_PER_HOUR = 3_600L
    const val HOURS_PER_DAY = 24.0

    /**
     * Sea level, and it is only the *temperature* this feeds, which only decides rain versus snow.
     *
     * A column's real ground height would need a chunk on the tick thread for a decision this is already
     * lenient about, so a scar high on a mountain may be credited with rain that actually fell as snow. Worth
     * revisiting when something else on this path already has the elevation in hand.
     */
    const val ELEVATION_METRES = 0.0

    /** Precipitation that lies on a burn rather than washing it. */
    val FROZEN = setOf(WeatherKind.SNOW, WeatherKind.BLIZZARD)

    /**
     * How far back a first integration may reach, in Bestia days, and the same in seconds.
     *
     * Three days is 72 hourly steps and, at the default speed factor, about 24 real hours - so a server down
     * overnight still heals what it should have.
     */
    const val CATCH_UP_DAYS = 3L
    const val CATCH_UP_SECONDS = CATCH_UP_DAYS * 24L * BESTIA_SECONDS_PER_HOUR

    /** A hard stop, so a clock jump cannot turn one pass into an unbounded loop on the tick thread. */
    const val MAX_STEPS_PER_PASS = 128

    /**
     * Millimetres of rain worth one full pass of erosion - `ScorchRegrowthSystem.MAX_ERODE_STEPS` of it.
     *
     * Deliberately not "enough to heal any scar". Progress is unbounded, so a burn wider than twice the step
     * count keeps eroding on later passes and simply takes longer - which is the behaviour worth having: a
     * scorched hillside should outlast a singed verge rather than both greening on the same schedule.
     *
     * At `rainRateMmPerDay = 120` and `meanWetIntensity = 0.5` a wet day delivers around 60 mm, so this is
     * roughly two wet days: long enough that a burn reads as something that happened, short enough that a
     * grassland does not stay black.
     */
    const val HEAL_RAIN_MM = 40.0
  }
}
