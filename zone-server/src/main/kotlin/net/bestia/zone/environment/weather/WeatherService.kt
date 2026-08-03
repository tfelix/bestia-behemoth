package net.bestia.zone.environment.weather

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.climate.LocalTemperature
import net.bestia.worldgen.climate.Temperature
import net.bestia.worldgen.climate.WeatherModel
import net.bestia.worldgen.climate.WeatherState
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.environment.time.BestiaDateTime
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/** What a sensed change is, and how far ahead it was seen. */
data class Forecast(val kind: net.bestia.worldgen.climate.WeatherKind, val inRealSeconds: Int)

/** The weather at one position, with the temperature that goes with it. */
data class WeatherAt(
  val regionId: Int,
  val state: WeatherState,
  val temperature: Temperature
)

/**
 * The weather, for the server.
 *
 * ### There is no table here, and there must not be one
 *
 * `MasterSpawnPointService` caches its equally-pure answer in a repository, and it is right to: an account row
 * references a chosen spawn point by identity, so that identity has to outlive the process. **Weather has no
 * such referent.** It is a pure function of `(seed, region, t)`, so a process-lifetime memo cannot go stale
 * while a table can - a table would be weather from a generator that no longer exists, indistinguishable from
 * weather from this one.
 *
 * The region partition and each region's climatology *are* timeless and so could legitimately be stored. They
 * are not, because they cost microseconds to rebuild (one Poisson sample of about fifty points and one pass
 * over the climate grid) against half a second for the whole world tier they sit on top of.
 *
 * ### `BestiaClock`'s first consumer
 *
 * That clock has existed as a Spring service with **zero production consumers**. This is the first, and it is
 * also what forced the calendar fix: `Season.ofMonth` used to run summer, winter, fall, spring, which is not a
 * year any single seasonal curve can express.
 */
@Service
class WeatherService(
  private val worldService: WorldService,
  private val clock: BestiaClock
) {

  private val model: WeatherModel by lazy { build() }

  private val temperature: LocalTemperature by lazy {
    LocalTemperature.from(
      worldService.generated.world.layers,
      worldService.generated.config,
      worldService.generated.params.climate,
      worldService.generated.params.weather
    ) ?: error("the world has no climate layers; weather cannot be modelled on it")
  }

  private fun build(): WeatherModel {
    val generated = worldService.generated
    val built = WeatherModel.of(generated, generated.params.weather)

    val aboveStormFloor = built.regions.regions.count {
      maxOf(it.meanMana, 0.6 * it.peakMana) >= generated.params.weather.manaStormFloor
    }

    // Logged in the shape of WorldService's own readiness line, and it is the boot-time smoke test for this
    // whole subsystem: a zero region count or a zero wet fraction here is the "complete, tested and never
    // reached" failure showing itself before anybody has to walk around looking for rain.
    val meanWet = built.regions.regions
      .filter { it.landShare > 0.0 }
      .map { built.wetDayProbability(it, ANNUAL_SAMPLE_POINT) }
      .average()

    LOG.info {
      "Weather: ${built.regions.count} regions at ~16 km, ${built.regions.inhabitedCount} with land, " +
          "$aboveStormFloor above the mana-storm floor, mean wet fraction ${"%.2f".format(meanWet)}"
    }

    return built
  }

  /** The weather now, at a voxel position and the ground elevation there. */
  fun at(voxelX: Long, voxelY: Long, elevationMetres: Double): WeatherAt =
    at(voxelX, voxelY, elevationMetres, clock.now())

  /**
   * The weather at an arbitrary instant - the same code path as [at], which is the whole point.
   *
   * `WEATHER_SENSE` promises to see changes ahead of time, and the field is `f(seed, region, t)` with no state
   * in it, so a forecast is this function at a later `t` and is exactly what the player will get. A Markov
   * chain could only have answered in distribution.
   */
  fun at(voxelX: Long, voxelY: Long, elevationMetres: Double, when_: BestiaDateTime): WeatherAt {
    val config = worldService.generated.config
    val worldX = voxelX * config.voxelSize
    val worldY = voxelY * config.voxelSize

    val region = model.regions.regionAt(worldX, worldY)
    val day = when_.absoluteDay

    // The air temperature *without* weather first, because the classification needs to know whether
    // precipitation falls as snow before it can say what the weather is.
    val dry = temperature.at(
      worldX, worldY, elevationMetres, when_.yearProgress, when_.timeOfDay
    )
    val state = model.at(region, day, dry.airCelsius)

    // Then again with the weather, which cools it. Two evaluations rather than one, and the order is forced:
    // snow-or-rain is decided by the temperature the weather has not yet touched.
    val withWeather = temperature.at(
      worldX, worldY, elevationMetres, when_.yearProgress, when_.timeOfDay, state
    )

    return WeatherAt(region.index, state, withWeather)
  }

  /**
   * The next change this position will see inside [realSecondsAhead], or null if nothing changes.
   *
   * What `WEATHER_SENSE` sells. It is the **actual** weather rather than a probability, and that follows from
   * the field being a pure function of `(seed, region, t)`: a forecast is [at] evaluated at a later `t`, along
   * the same code path, so what the player is told is exactly what they will get. A Markov chain - the textbook
   * choice - could only have answered in distribution, which is why it was not the choice.
   *
   * Stepped rather than solved because the field has no inverse; the step is a Bestia hour, which is finer than
   * the synoptic period by a factor of thirty.
   */
  fun forecast(
    voxelX: Long,
    voxelY: Long,
    elevationMetres: Double,
    realSecondsAhead: Int
  ): Forecast? {
    val now = clock.now()
    val current = at(voxelX, voxelY, elevationMetres, now).state.kind

    val bestiaSecondsAhead = realSecondsAhead * clock.speedFactor
    val steps = (bestiaSecondsAhead / BESTIA_SECONDS_PER_HOUR).toInt()
    if (steps <= 0) return null

    for (step in 1..steps) {
      val ahead = clock.after(now, (step * BESTIA_SECONDS_PER_HOUR).toLong())
      val kind = at(voxelX, voxelY, elevationMetres, ahead).state.kind
      if (kind != current) {
        val realSeconds = (step * BESTIA_SECONDS_PER_HOUR / clock.speedFactor).toInt()
        return Forecast(kind, realSeconds)
      }
    }

    return null
  }

  /** Which weather region covers a voxel position. An opaque token; see the `.proto`. */
  fun regionOf(voxelX: Long, voxelY: Long): Int {
    val config = worldService.generated.config
    return model.regions.regionAt(voxelX * config.voxelSize, voxelY * config.voxelSize).index
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** Bestia seconds in a Bestia hour. The forecast's step. */
    private const val BESTIA_SECONDS_PER_HOUR = 3_600.0

    /** Fraction of the year the boot-time mean wet fraction is sampled at. Arbitrary and only for the log. */
    private const val ANNUAL_SAMPLE_POINT = 0.4
  }
}
