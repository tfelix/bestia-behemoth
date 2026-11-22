package net.bestia.zone.battle.ecs.status

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * What running out of stamina costs a master or an owned bestia; see [StaminaExhaustionSystem].
 *
 * The bands and the speed curve are the docs' and live in code ([StaminaBand], [StaminaExhaustionSystem.speedPercentAt]);
 * only the drain's pace is a tuning knob.
 */
@ConfigurationProperties("stamina")
data class StaminaConfig(
  /** Real seconds between two drains of an entity below 5 % stamina. */
  val drainIntervalSeconds: Float = 30f,

  /** Share of max health one drain takes, in percent. Never less than 1 HP. */
  val drainHealthPercent: Double = 0.3,
) {
  init {
    require(drainIntervalSeconds > 0f) { "drainIntervalSeconds must be positive" }
    require(drainHealthPercent > 0.0) { "drainHealthPercent must be positive" }
  }
}
