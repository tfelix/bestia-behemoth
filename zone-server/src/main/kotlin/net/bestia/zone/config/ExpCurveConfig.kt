package net.bestia.zone.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * How long a level takes someone who does nothing but earn EXP. The EXP a level costs is derived from it, so
 * fighting and working take the same time per level by construction.
 */
@ConfigurationProperties(prefix = "exp-curve")
data class ExpCurveConfig(
  /** The level [anchorHours] is measured at. */
  val anchorLevel: Int = 97,
  /** Hours of constant play one level takes at [anchorLevel]. */
  val anchorHours: Double = 24.0,
  /** How fast a level's time grows with the level. Above 2, because craft time grows with its square. */
  val exponent: Double = 2.5,
  /** No level takes less than this, which keeps the first levels quick. */
  val floorSeconds: Int = 60,
  /** What one kill of an on-level enemy takes, walking and recovering included. Turns time into kills and EXP. */
  val referenceKillSeconds: Int = 25,
) {
  init {
    require(anchorLevel > 0) { "exp-curve.anchor-level must be positive, was $anchorLevel" }
    require(anchorHours > 0) { "exp-curve.anchor-hours must be positive, was $anchorHours" }
    require(exponent > 0) { "exp-curve.exponent must be positive, was $exponent" }
    require(floorSeconds > 0) { "exp-curve.floor-seconds must be positive, was $floorSeconds" }
    require(referenceKillSeconds > 0) { "exp-curve.reference-kill-seconds must be positive, was $referenceKillSeconds" }
  }
}
