package net.bestia.zone.weather

import kotlin.math.exp

/**
 * Snowpack and ground wetness at evenly spaced elevations across one weather region.
 *
 * Built by [replay] from a window of [HourSample]s, starting from bare dry ground. Every band sees the same
 * precipitation and higher bands are colder, so snow can only grow with elevation. That is what makes a single
 * snow line meaningful.
 */
class GroundBands private constructor(
  private val config: GroundWeatherConfig,
  private val snowMm: DoubleArray,
  private val wetness: DoubleArray,
) {

  fun coverAt(elevation: Double): GroundCover {
    val snowLine = lowestElevationWith(config.patchySnowMm)
    // Patchy snow that never deepens to full cover inside the bands is still patchy at the top of them.
    val snowFull = lowestElevationWith(config.fullSnowMm) ?: snowLine?.let { config.highestBandMetres }

    return GroundCover(wetness = interpolate(wetness, elevation), snowLine = snowLine, snowFull = snowFull)
  }

  /** Where the snow first reaches [snowMm], interpolated between the two bands around it. */
  private fun lowestElevationWith(snowMm: Double): Double? {
    val first = this.snowMm.indexOfFirst { it >= snowMm }
    if (first < 0) return null
    if (first == 0) return elevationOf(0)

    val below = this.snowMm[first - 1]
    val share = (snowMm - below) / (this.snowMm[first] - below)
    return elevationOf(first - 1) + share * config.bandMetres
  }

  private fun interpolate(values: DoubleArray, elevation: Double): Double {
    val position = ((elevation - config.lowestBandMetres) / config.bandMetres).coerceIn(0.0, values.size - 1.0)
    val lower = position.toInt().coerceAtMost(values.size - 2)
    val share = position - lower
    return values[lower] * (1.0 - share) + values[lower + 1] * share
  }

  private fun elevationOf(band: Int): Double {
    return config.lowestBandMetres + band * config.bandMetres
  }

  companion object {

    private const val FREEZING = 0.0

    /**
     * Plays [samples] forward, oldest first, from bare dry ground.
     *
     * @param lapseRate degrees the air cools per thousand metres of elevation
     */
    fun replay(samples: List<HourSample>, lapseRate: Double, config: GroundWeatherConfig): GroundBands {
      val count = ((config.highestBandMetres - config.lowestBandMetres) / config.bandMetres).toInt() + 1
      val snow = DoubleArray(count)
      val wet = DoubleArray(count)
      val drying = exp(-1.0 / config.dryingHours)

      for (sample in samples) {
        for (band in 0 until count) {
          val elevation = config.lowestBandMetres + band * config.bandMetres
          val celsius = sample.seaLevelCelsius - lapseRate * elevation / 1_000.0

          var waterMm = 0.0
          if (celsius < FREEZING) {
            snow[band] += sample.precipitationMm
          } else {
            val meltMm = minOf(snow[band], config.meltMmPerDegreeHour * celsius)
            snow[band] -= meltMm
            waterMm = sample.precipitationMm + meltMm
          }

          wet[band] = 1.0 - (1.0 - wet[band] * drying) * exp(-waterMm / config.soakMm)
        }
      }

      return GroundBands(config, snow, wet)
    }
  }
}
