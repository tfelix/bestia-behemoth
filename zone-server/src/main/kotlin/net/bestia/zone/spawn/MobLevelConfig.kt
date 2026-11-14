package net.bestia.zone.spawn

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * How steeply a wild creature's strength follows the level it rolled, see [MobLevelScaling].
 */
@ConfigurationProperties(prefix = "mob-level")
data class MobLevelConfig(
  /**
   * Added to both levels in `(rolled + offset) / (reference + offset)`, which is what keeps a one-level
   * difference worth a few percent in the high brackets but not a third of a Lv 3 creature's health.
   *
   * Zero makes strength proportional to level; larger values flatten the spread. A tuning knob, not a
   * calibration - the authored numbers are only exact at the reference level.
   */
  val scalingOffset: Int = 10
) {
  init {
    require(scalingOffset >= 0) { "mob-level.scaling-offset must not be negative, was $scalingOffset" }
  }
}
