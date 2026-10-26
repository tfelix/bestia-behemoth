package net.bestia.zone.weather

import net.bestia.worldgen.climate.WeatherKind
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * The snow and the wet the weather has left on the ground, the same for every player.
 *
 * Each region replays its last [GroundWeatherConfig.replayHours] hours from bare ground, so the answer is a
 * pure function of the region and the hour, like the weather itself. One weather evaluation per region per
 * Bestia hour feeds it: elevation only changes the temperature, which [GroundBands] derives.
 */
@Service
class GroundWeather(
  private val weatherService: WeatherService,
  private val worldService: WorldService,
  private val clock: BestiaClock,
  private val config: GroundWeatherConfig,
) {

  private val histories = ConcurrentHashMap<Int, RegionHistory>()

  fun at(voxelX: Long, voxelY: Long, groundElevation: Double): GroundCover {
    val regionId = weatherService.regionOf(voxelX, voxelY)
    val hour = Math.floorDiv(clock.now().absoluteSecond, BESTIA_SECONDS_PER_HOUR)

    val history = histories.compute(regionId) { _, known -> advance(known ?: RegionHistory(regionId), hour) }!!

    return history.bands.coverAt(groundElevation)
  }

  private fun advance(history: RegionHistory, hour: Long): RegionHistory {
    if (history.throughHour == hour) return history

    // Only the hours the window has not seen yet are evaluated. A clock jumped by `/date` starts over.
    val continues = history.samples.isNotEmpty() &&
        hour > history.throughHour && hour - history.throughHour < config.replayHours
    if (!continues) history.samples.clear()
    val firstNew = if (continues) history.throughHour + 1 else hour - config.replayHours + 1

    for (sampled in firstNew..hour) {
      history.samples.addLast(sample(history.regionId, sampled))
    }
    while (history.samples.size > config.replayHours) {
      history.samples.removeFirst()
    }

    history.throughHour = hour
    history.bands = GroundBands.replay(history.samples, weatherService.lapseRate, config)

    return history
  }

  private fun sample(regionId: Int, hour: Long): HourSample {
    val (voxelX, voxelY) = weatherService.regionCentreVoxel(regionId)
    val at = weatherService.at(voxelX, voxelY, 0.0, clock.atAbsoluteSecond(hour * BESTIA_SECONDS_PER_HOUR))

    val rainPerHour = worldService.generated.params.weather.rainRateMmPerDay / HOURS_PER_DAY
    val precipitationMm = if (at.state.kind in PRECIPITATING) at.state.intensity * rainPerHour else 0.0

    return HourSample(precipitationMm, at.temperature.airCelsius)
  }

  private class RegionHistory(val regionId: Int) {
    val samples = ArrayDeque<HourSample>()
    var throughHour = Long.MIN_VALUE
    lateinit var bands: GroundBands
  }

  private companion object {
    const val BESTIA_SECONDS_PER_HOUR = 3_600L
    const val HOURS_PER_DAY = 24.0

    /** Whether it falls as rain or as snow is decided per elevation band, so both count here. */
    val PRECIPITATING = setOf(
      WeatherKind.RAIN, WeatherKind.HEAVY_RAIN, WeatherKind.THUNDERSTORM, WeatherKind.TORNADO,
      WeatherKind.SNOW, WeatherKind.BLIZZARD,
    )
  }
}
