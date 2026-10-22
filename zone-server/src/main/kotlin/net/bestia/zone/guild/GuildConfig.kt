package net.bestia.zone.guild

import org.springframework.boot.context.properties.ConfigurationProperties

/** How big guilds get, how fast they level, and the bounds a rank's tax may be set within. */
@ConfigurationProperties(prefix = "guild")
data class GuildConfig(
  val nameMaxLength: Int = 24,
  val rankNameMaxLength: Int = 20,
  /** Seats in a new guild. */
  val baseMaxMembers: Int = 16,
  /** Seats each Guild Extension level adds. */
  val membersPerExtensionLevel: Int = 4,
  val maxLevel: Int = 50,
  /** Scales the whole level curve; see [GuildLevels.requiredExp]. */
  val expUnit: Long = 2_000,
  /** Ranks in a guild, the leader's included. */
  val maxRanks: Int = 10,
  val defaultTaxPercent: Int = 5,
  val minTaxPercent: Int = 1,
  val maxTaxPercent: Int = 80,
  val invitationTimeoutSeconds: Long = 60,
  /** How often taxed EXP is written to the guilds; a crash loses at most this much. */
  val expFlushSeconds: Long = 30,
  /** How far from the guild leader the stat auras reach, measured flat. */
  val auraRadiusMetres: Int = 15,
  /** Fortune's Favor raises each drop's own chance by this share per level. */
  val fortunePercentPerLevel: Int = 5,
) {
  init {
    require(nameMaxLength > 0) { "guild.name-max-length must be positive, was $nameMaxLength" }
    require(rankNameMaxLength > 0) { "guild.rank-name-max-length must be positive, was $rankNameMaxLength" }
    require(baseMaxMembers >= 1) { "guild.base-max-members must seat the leader, was $baseMaxMembers" }
    require(membersPerExtensionLevel >= 0) { "guild.members-per-extension-level must not be negative" }
    require(maxLevel >= 1) { "guild.max-level must be at least 1, was $maxLevel" }
    require(expUnit > 0) { "guild.exp-unit must be positive, was $expUnit" }
    require(maxRanks >= 2) { "guild.max-ranks must leave a rank below the leader's, was $maxRanks" }
    require(minTaxPercent in 0..maxTaxPercent && maxTaxPercent <= 100) {
      "guild tax bounds must satisfy 0 <= min <= max <= 100, were $minTaxPercent..$maxTaxPercent"
    }
    require(defaultTaxPercent in minTaxPercent..maxTaxPercent) {
      "guild.default-tax-percent must lie within the tax bounds, was $defaultTaxPercent"
    }
    require(invitationTimeoutSeconds > 0) { "guild.invitation-timeout-seconds must be positive" }
    require(expFlushSeconds > 0) { "guild.exp-flush-seconds must be positive" }
    require(auraRadiusMetres > 0) { "guild.aura-radius-metres must be positive" }
    require(fortunePercentPerLevel >= 0) { "guild.fortune-percent-per-level must not be negative" }
  }
}
