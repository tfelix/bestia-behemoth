package net.bestia.zone.world.ground

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Runtime settings for blood soaking into the ground.
 *
 * Runtime rather than world settings, as `GroundWearConfig` is. What is stored is a *level* rather than a
 * rate, so retuning [fadeSeconds] reshapes how the stains that already exist fade rather than invalidating
 * them.
 *
 * @property fadeSeconds Bestia seconds for a full cell to dry out completely. Five Bestia days, which at
 *   `SPEED_FACTOR` 3 is about forty real hours - long enough that a battle site still reads as one the next
 *   day, which is the whole reason blood is a lasting mark rather than an effect.
 * @property spillWeight how much lands on the tile something died on, out of the 255 a cell holds. High, and
 *   deliberately: unlike wear this is written once and never topped up, so the first stain is the whole stain.
 * @property spillRadiusTiles how far the pool reaches, with a falloff to nothing at its edge.
 * @property visibleThreshold the level below which a cell is not sent. Lower than wear's, because there is no
 *   diffuse background of blood to keep under notice - a stain is either there or it is not.
 * @property flushIntervalSeconds how often changed columns are written out.
 * @property maxResidentColumns bloodied columns held in memory at once.
 */
@ConfigurationProperties(prefix = "ground-blood")
data class GroundBloodConfig(
  override val fadeSeconds: Long = 432_000,
  val spillWeight: Int = 220,
  val spillRadiusTiles: Int = 2,
  override val visibleThreshold: Int = 16,
  override val flushIntervalSeconds: Float = 120f,
  override val maxResidentColumns: Int = 4_096,
) : GroundLevelConfig {

  init {
    require(fadeSeconds > 0) { "fadeSeconds must be positive, was $fadeSeconds" }
    require(spillWeight in 1..255) { "spillWeight must fit a level, was $spillWeight" }
    require(spillRadiusTiles >= 0) { "spillRadiusTiles cannot be negative, was $spillRadiusTiles" }
    require(visibleThreshold in 0..255) { "visibleThreshold must fit a level, was $visibleThreshold" }
    require(maxResidentColumns > 0) { "maxResidentColumns must be positive, was $maxResidentColumns" }
  }
}
