package net.bestia.zone.doomsday

import org.springframework.boot.context.properties.ConfigurationProperties

/** The doomsday's timing, in real days, and the levers for testing it. See the doomsday server page. */
@ConfigurationProperties("doomsday")
data class DoomsdayConfig(
  val enabled: Boolean = true,

  /** The threat sets in this many days after the world is born, plus up to [onsetSpreadDays] more. */
  val onsetMinDays: Double = 180.0,
  val onsetSpreadDays: Double = 90.0,

  /** How long the threat takes to run its course when nobody acts. */
  val unfoldMinDays: Double = 180.0,
  val unfoldMaxDays: Double = 365.0,

  /** The Foreshadowing strikes at this share of the time before onset. */
  val foreshadowAtMin: Double = 0.4,
  val foreshadowAtMax: Double = 0.6,

  /** How long the Foreshadowing lasts, and how far its outcome moves the onset, in days. */
  val foreshadowMinDays: Double = 7.0,
  val foreshadowMaxDays: Double = 14.0,
  val foreshadowShiftDays: Double = 14.0,

  /** A world still standing at this share of its life span, in percent, is saved. */
  val savedAtT: Double = 150.0,

  /** Multiplies how fast the world ages for the doomsday. 30 makes a real day count as a month, for test servers. */
  val timeScale: Double = 1.0,

  val minPace: Double = 0.25,
  val maxPace: Double = 3.0,
  val stageHysteresis: Double = 0.02,

  val endWindowHours: Double = 24.0,
  val lastStandPushBackTo: Double = 0.85,
  val endSequenceSeconds: Long = 90,

  /** Alpha-test overrides for a new world: this threat, this Foreshadowing, this origin in metres. */
  val forceKind: DoomKind? = null,
  val forceForeshadow: ForeshadowKind? = null,
  val forceOriginX: Double? = null,
  val forceOriginY: Double? = null,
)
