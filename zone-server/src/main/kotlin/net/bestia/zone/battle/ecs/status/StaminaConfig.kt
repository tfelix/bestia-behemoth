package net.bestia.zone.battle.ecs.status

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * How stamina is spent and won back by a master or an owned bestia: what running out costs
 * ([StaminaExhaustionSystem]), what work costs ([WorkStrainSystem]) and how far rest alone brings it back
 * ([StaminaRegenSystem]).
 *
 * The bands and the speed curve are the docs' and live in code ([StaminaBand], [StaminaExhaustionSystem.speedPercentAt]).
 * The rest are tuning knobs, set so that food is what keeps a worker going: a night of work eats about a meal an hour.
 */
@ConfigurationProperties("stamina")
data class StaminaConfig(
  /** Real seconds between two drains of an entity below 5 % stamina. */
  val drainIntervalSeconds: Float = 30f,

  /** Share of max health one drain takes, in percent. Never less than 1 HP. */
  val drainHealthPercent: Double = 0.3,

  /** Rest alone brings stamina back up to this share of the maximum. Food fills the rest. */
  val regenCapPercent: Int = 30,

  /**
   * How long after the weather last bit a body still gets nothing back from rest. Twice the exposure system's
   * interval (`weather.exposure.interval-seconds`), which covers the gap between two of its weighings.
   */
  val exposureMemorySeconds: Float = 10f,

  /** Resting regains this share of the base rate in `RegenerationCalculator.staminaRegen`. */
  val restRegenPercent: Int = 50,

  /** How long after its last unit of work a worker regains no stamina but from food. */
  val wornOutSeconds: Double = 30.0 * 60.0,

  /** What an hour of labour costs, spread over the units of work by their [Labour] seconds. */
  val staminaPerLabourHour: Double = 35.0,

  /** The labour one prop picked up is worth: a stick, a herb, a crystal. */
  val collectLabourSeconds: Float = 60f,

  /** The labour one tree felled is worth. */
  val fellLabourSeconds: Float = 300f,
) {
  init {
    require(drainIntervalSeconds > 0f) { "drainIntervalSeconds must be positive" }
    require(drainHealthPercent > 0.0) { "drainHealthPercent must be positive" }
    require(regenCapPercent in 1..100) { "regenCapPercent must be within 1..100" }
    require(restRegenPercent > 0) { "restRegenPercent must be positive" }
    require(staminaPerLabourHour >= 0.0) { "staminaPerLabourHour must not be negative" }
  }
}
