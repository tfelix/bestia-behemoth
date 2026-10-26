package net.bestia.zone.weather

/**
 * One Bestia hour of a region's weather, as much as the ground needs of it.
 *
 * The temperature is taken at elevation zero. The air cools linearly with height, so this one number gives the
 * temperature of every elevation band, and whether the precipitation fell there as snow.
 */
data class HourSample(
  /** Precipitation that hour, in millimetres of water. */
  val precipitationMm: Double,
  val seaLevelCelsius: Double,
)
