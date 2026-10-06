package net.bestia.zone.ai.ecs

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * How many times less often [AiThrottle] runs an agent no player is near. The keys sit under `ambient-spawn`
 * because ambient creatures needed them first, but every [AiThrottleable] agent uses them.
 */
@ConfigurationProperties(prefix = "ambient-spawn")
data class AiThrottleConfig(
  /**
   * How many times less often an agent at `AiDetail.REDUCED` is processed: seen, but with no player near.
   *
   * 1 disables it without a code change. Creatures without [AiThrottleable] - a den's pack, a boss, a
   * `/spawn`ed mob - never drop below this tier, and anything a player controls is never slowed at all.
   */
  val throttleFactor: Int = 4,

  /** How many times less often an agent nobody can see is processed; see `AiDetail.BACKGROUND`. */
  val backgroundFactor: Int = 20,
) {

  init {
    require(throttleFactor >= 1) { "throttle-factor must be at least 1, was $throttleFactor" }
    require(backgroundFactor >= 1) { "background-factor must be at least 1, was $backgroundFactor" }
  }
}
