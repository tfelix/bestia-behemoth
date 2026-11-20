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
  /** A master discovers a seed by walking within this of its centre: the seed's core. */
  val coreRadiusMetres: Double = 5.0,
  /** Official Guild Approval senses the awake, free seeds this far away. */
  val senseRangeMetres: Double = 30_000.0,
  /** It shows at most this many of them, nearest first. */
  val senseMarks: Int = 3,
  /** A sensed mark is off by up to this share of its distance. */
  val senseErrorShare: Double = 1.0 / 3.0,
) {
  init {
    require(minAwakeSeeds >= 0) { "stronghold.min-awake-seeds must not be negative, was $minAwakeSeeds" }
    require(guildsPerSeed > 0) { "stronghold.guilds-per-seed must be positive, was $guildsPerSeed" }
    require(settlementClearanceMetres >= 0.0) { "stronghold.settlement-clearance-metres must not be negative" }
    require(coreRadiusMetres > 0.0) { "stronghold.core-radius-metres must be positive, was $coreRadiusMetres" }
    require(senseRangeMetres > 0.0) { "stronghold.sense-range-metres must be positive, was $senseRangeMetres" }
    require(senseMarks > 0) { "stronghold.sense-marks must be positive, was $senseMarks" }
    require(senseErrorShare in 0.0..1.0) { "stronghold.sense-error-share must lie in 0..1, was $senseErrorShare" }
  }
}
