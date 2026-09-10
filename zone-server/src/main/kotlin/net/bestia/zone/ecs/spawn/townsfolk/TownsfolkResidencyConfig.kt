package net.bestia.zone.ecs.spawn.townsfolk

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * How much of a town is standing in it, and what that is allowed to cost.
 *
 * Everything here is a runtime setting, for `AmbientSpawnConfig`'s reason and more strongly: no townsperson
 * is persisted and nothing about one is folded into a world version, so every knob takes effect on a
 * restart and none of them can cost a world.
 *
 * There is deliberately no throttle knob here. Townsfolk carry `AiThrottleable` from birth, and `AiThrottle`
 * reads one cadence for everything that opts in - `ambient-spawn.throttle-factor`. A second number would be
 * one this class could not actually apply.
 */
@ConfigurationProperties(prefix = "townsfolk")
data class TownsfolkResidencyConfig(
  /** Off leaves the empty towns this was added to. The knob to reach for first when in doubt. */
  val enabled: Boolean = true,

  /**
   * How near a player has to be for somebody to be standing there, in tiles.
   *
   * Measured against where the person would *be* rather than where they live, which is what makes a
   * radius work at all here: a farmer's field can be a kilometre from their house, and a ring wide enough
   * to cover both from either end would populate half a city to show one man hoeing.
   *
   * Matched to `ambient-spawn.activation-radius-tiles` for the same reason it was chosen there - the
   * interest radius plus the widest a person strays plus margin, so somebody is standing before they can
   * be seen.
   */
  val activationRadiusTiles: Long = 240,

  /**
   * People created per pass, across all players.
   *
   * Independent of the ambient budget, so a player at a city edge draws both allocations at once. That is
   * worth knowing before either is raised - see `AmbientSpawnDensityTest`, which measures the other half.
   */
  val spawnsPerPass: Int = 12,

  /** Matches the ambient layer: stepping away and back must not empty a street and refill it. */
  val unloadDelaySeconds: Float = 60f,
)
