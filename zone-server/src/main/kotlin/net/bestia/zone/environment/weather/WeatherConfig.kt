package net.bestia.zone.environment.weather

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * How often the weather is re-evaluated, and how much has to change before a player is told.
 *
 * The bands exist because every channel is continuous: without them a value sitting on a threshold sends a
 * message every interval and the client crossfades forever. See [WeatherSystem].
 */
@ConfigurationProperties("weather")
data class WeatherConfig(

  /** Set false to stop evaluating weather entirely. Nothing else changes; the client simply keeps a clear sky. */
  val enabled: Boolean = true,

  /**
   * Real seconds between evaluations.
   *
   * Ten, against a Bestia day of eight real hours: weather changes on a day scale, so this is already far
   * finer than the thing being measured. It is this short only so that walking across a region border is felt
   * promptly rather than up to a minute later.
   */
  val evaluationSeconds: Float = 10f,

  /**
   * Real seconds after which a player is told again even if nothing changed.
   *
   * A resync, not a refresh: a client that missed one message would otherwise believe in the old sky until the
   * weather next moved past a band, which on a calm day is hours.
   */
  val heartbeatSeconds: Int = 120,

  val cloudBand: Double = 0.12,
  val intensityBand: Double = 0.12,

  /** Degrees Celsius. Small, because a player climbing a hillside should watch it fall. */
  val temperatureBand: Double = 1.0,

  /** Metres per second. */
  val windBand: Double = 1.5
)
