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

  /**
   * Coin one ore voxel of gold mints.
   *
   * The whole chain's free parameter, and the only one: everything else - how much gold a world holds,
   * how many voxels a ton is - is fixed by the generator. This is what ties the two together, so it is
   * also what has to move if a world is to hold more coin without holding more gold.
   */
  val coinsPerOreVoxel: Long = 10_000,

  /** Coin the supply must stretch to per concurrent player, held and unmined together. */
  val coinsPerPlayer: Long = 1_000_000,
)
