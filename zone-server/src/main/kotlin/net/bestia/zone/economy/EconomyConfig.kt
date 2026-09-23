package net.bestia.zone.economy

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * How much coin a world can ever hold, and how much of it starts in NPC hands.
 *
 * Birth settings. Raising [coinSupply] under a running world moves every treasury's target without
 * moving the coin players already hold, so the two drift apart until the reserve absorbs the difference.
 *
 * The default is derived from the gold the generator places on a Genesis world rather than chosen; see
 * `docs/server/money-supply` in bestia-docs.
 */
@ConfigurationProperties(prefix = "economy")
data class EconomyConfig(

  /** Every coin the world can ever hold, minted and unminted alike. */
  val coinSupply: Long = 300_000_000,

  /** Share of [coinSupply] sitting in settlement treasuries when a world is created. */
  val npcShare: Double = 0.5,
)
