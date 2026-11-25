package net.bestia.zone.worldevent

import org.springframework.boot.context.properties.ConfigurationProperties

/** How often the director may start an event in a province. */
@ConfigurationProperties("world-events")
data class WorldEventConfig(
  val enabled: Boolean = true,

  /** How much a province's budget grows each Bestia day. */
  val budgetPerBestiaDay: Double = 1.0,

  /** The most budget a province saves up. */
  val budgetCap: Double = 5.0,

  /** Bestia days of quiet after an event in a province before the next may start there. */
  val quietBestiaDays: Double = 3.0,

  /**
   * The chance per run that a ready province does start an event, so an event does not land the moment it can
   * be afforded. At one run per five minutes, a quarter means about twenty minutes on average.
   */
  val startChance: Double = 0.25,
)
