package net.bestia.zone.weather

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * How the ground keeps the weather: how long snow and wet last, and how it is sampled.
 *
 * Elevations are in the unit `ChunkService.surfaceElevationAt` answers in, which is also what the temperature
 * model is fed. Millimetres are water equivalent.
 */
@ConfigurationProperties("ground-weather")
data class GroundWeatherConfig(

  /** Bestia hours of past weather each answer is replayed from. Snow older than this is forgotten. */
  val replayHours: Int = 144,

  val lowestBandMetres: Double = -500.0,
  val highestBandMetres: Double = 6_000.0,
  val bandMetres: Double = 50.0,

  /** Snow melted per degree above freezing per Bestia hour; about five millimetres per degree-day. */
  val meltMmPerDegreeHour: Double = 0.2,

  /** Water that wets dry ground to two thirds of fully soaked. */
  val soakMm: Double = 2.0,

  /** Bestia hours for wet ground to lose two thirds of its wetness. */
  val dryingHours: Double = 3.0,

  /** Snow from which the ground shows white patches, and from which it is white all over. */
  val patchySnowMm: Double = 1.0,
  val fullSnowMm: Double = 6.0,
) {
  init {
    require(replayHours > 0) { "replayHours must be positive, was $replayHours" }
    require(bandMetres > 0.0) { "bandMetres must be positive, was $bandMetres" }
    require(highestBandMetres > lowestBandMetres) { "highestBandMetres must be above lowestBandMetres" }
    require(soakMm > 0.0 && dryingHours > 0.0) { "soakMm and dryingHours must be positive" }
    require(fullSnowMm > patchySnowMm && patchySnowMm > 0.0) { "need 0 < patchySnowMm < fullSnowMm" }
  }
}
