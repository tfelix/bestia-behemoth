package net.bestia.zone.stronghold

import org.springframework.boot.context.properties.ConfigurationProperties

/** How many Empyrite Seeds are awake, and what keeps one from waking. See the Guild Strongholds page. */
@ConfigurationProperties(prefix = "stronghold")
data class StrongholdConfig(
  /** Awake at least this many seeds, however few guilds have Official Guild Approval. */
  val minAwakeSeeds: Int = 4,
  /** One more seed wakes for each this many approved guilds. */
  val guildsPerSeed: Int = 25,
  /** A seed this close to a player town or its sign site is skipped. Also the gap a new town keeps from a seed. */
  val settlementClearanceMetres: Double = 1_000.0,
) {
  init {
    require(minAwakeSeeds >= 0) { "stronghold.min-awake-seeds must not be negative, was $minAwakeSeeds" }
    require(guildsPerSeed > 0) { "stronghold.guilds-per-seed must be positive, was $guildsPerSeed" }
    require(settlementClearanceMetres >= 0.0) { "stronghold.settlement-clearance-metres must not be negative" }
  }
}
