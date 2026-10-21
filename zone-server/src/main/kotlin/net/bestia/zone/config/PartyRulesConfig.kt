package net.bestia.zone.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * How a party is sized and how it shares kill EXP.
 *
 * Here rather than in `party` because the EXP split is the death system's business, and that sits below the
 * party slice.
 */
@ConfigurationProperties(prefix = "party")
data class PartyRulesConfig(
  /** Seats in a party, the owner's included. Open invitations hold a seat each. */
  val maxSize: Int = 12,
  val nameMaxLength: Int = 20,
  /**
   * How far from a kill a member may stand and still get an Even share of it, measured flat on the map from the
   * member's master or the entity they play. Metres, which are position units.
   */
  val expShareRangeMetres: Int = 100,
  /** Added to an attacker's EXP when it is shared between at least two members, in percent. */
  val evenShareBonusPercent: Int = 30,
  val invitationTimeoutSeconds: Long = 60,
) {
  init {
    require(maxSize >= 2) { "party.max-size must seat at least two, was $maxSize" }
    require(nameMaxLength > 0) { "party.name-max-length must be positive, was $nameMaxLength" }
    require(expShareRangeMetres > 0) { "party.exp-share-range-metres must be positive, was $expShareRangeMetres" }
    require(evenShareBonusPercent >= 0) { "party.even-share-bonus-percent must not be negative, was $evenShareBonusPercent" }
    require(invitationTimeoutSeconds > 0) { "party.invitation-timeout-seconds must be positive, was $invitationTimeoutSeconds" }
  }
}
